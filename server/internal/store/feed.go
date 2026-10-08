package store

import (
	"context"
	"math"
	"sort"
	"time"
)

const DefaultNearbyRadiusKm = 80.0

type FeedFilter string

const (
	FeedAll     FeedFilter = "all"
	FeedNearby  FeedFilter = "nearby"
	FeedFriends FeedFilter = "friends"
	FeedGlobal  FeedFilter = "global"
	FeedPosts   FeedFilter = "posts"
)

type FeedAuthor struct {
	FriendCode  string `json:"friendCode"`
	DisplayName string `json:"displayName"`
}

type FeedItem struct {
	Type      string      `json:"type"`
	ID        string      `json:"id"`
	CreatedAt int64       `json:"createdAt"`
	Category  string      `json:"category,omitempty"`
	Author    *FeedAuthor `json:"author,omitempty"`
	Body      string      `json:"body,omitempty"`
	ImageKey  string      `json:"imageKey,omitempty"`
	Shoot     *Shoot      `json:"shoot,omitempty"`
}

type FeedInput struct {
	ViewerCode string
	Filter     FeedFilter
	Lat        *float64
	Lng        *float64
	RadiusKm   float64
	SinceMs    int64
}

func haversineKm(lat1, lng1, lat2, lng2 float64) float64 {
	const earthRadius = 6371.0
	rad := math.Pi / 180
	dLat := (lat2 - lat1) * rad
	dLng := (lng2 - lng1) * rad
	a := math.Sin(dLat/2)*math.Sin(dLat/2) +
		math.Cos(lat1*rad)*math.Cos(lat2*rad)*math.Sin(dLng/2)*math.Sin(dLng/2)
	c := 2 * math.Atan2(math.Sqrt(a), math.Sqrt(1-a))
	return earthRadius * c
}

func shootCategory(sh Shoot, viewerCode string, friendSet map[string]struct{}) string {
	if sh.IsCurated || sh.IsPublic {
		return "global"
	}
	if sh.HostFriendCode != nil {
		if *sh.HostFriendCode == viewerCode {
			return "friend"
		}
		if _, ok := friendSet[*sh.HostFriendCode]; ok {
			return "friend"
		}
	}
	return "friend"
}

func (s *Store) ListFeedItems(ctx context.Context, in FeedInput) ([]FeedItem, error) {
	fromMs := time.Now().Add(-30 * 24 * time.Hour).UnixMilli()
	if in.SinceMs > 0 && in.SinceMs < fromMs {
		fromMs = in.SinceMs
	}

	radius := in.RadiusKm
	if radius <= 0 {
		radius = DefaultNearbyRadiusKm
	}

	var items []FeedItem

	if in.Filter == FeedAll || in.Filter == FeedNearby || in.Filter == FeedFriends || in.Filter == FeedGlobal {
		shoots, err := s.ListVisibleShoots(ctx, in.ViewerCode, fromMs, 0)
		if err != nil {
			return nil, err
		}
		friendCodes, err := s.ListFriendCodes(ctx, in.ViewerCode)
		if err != nil {
			return nil, err
		}
		friendSet := make(map[string]struct{}, len(friendCodes))
		for _, c := range friendCodes {
			friendSet[c] = struct{}{}
		}

		for i := range shoots {
			sh := shoots[i]
			cat := shootCategory(sh, in.ViewerCode, friendSet)

			if sh.Latitude != nil && sh.Longitude != nil && in.Lat != nil && in.Lng != nil {
				dist := haversineKm(*in.Lat, *in.Lng, *sh.Latitude, *sh.Longitude)
				sh.DistanceKm = &dist
			}

			include := false
			switch in.Filter {
			case FeedAll:
				include = true
			case FeedGlobal:
				include = sh.IsCurated || sh.IsPublic
			case FeedFriends:
				include = !sh.IsCurated && !sh.IsPublic
			case FeedNearby:
				if sh.Latitude != nil && sh.Longitude != nil && in.Lat != nil && in.Lng != nil {
					if sh.DistanceKm != nil && *sh.DistanceKm <= radius {
						include = true
					}
				}
			}

			if !include {
				continue
			}

			items = append(items, FeedItem{
				Type:      "shoot",
				ID:        "shoot-" + sh.ID,
				CreatedAt: sh.StartsAt,
				Category:  cat,
				Shoot:     &sh,
			})
		}
	}

	if in.Filter == FeedAll || in.Filter == FeedPosts {
		posts, err := s.ListVisiblePosts(ctx, in.ViewerCode, fromMs)
		if err != nil {
			return nil, err
		}
		for _, p := range posts {
			items = append(items, FeedItem{
				Type:      "post",
				ID:        "post-" + p.ID,
				CreatedAt: p.CreatedAt,
				Category:  "post",
				Author: &FeedAuthor{
					FriendCode:  p.AuthorCode,
					DisplayName: p.AuthorName,
				},
				Body:     p.Body,
				ImageKey: p.ImageKey,
			})
		}
	}

	sort.Slice(items, func(i, j int) bool {
		return items[i].CreatedAt > items[j].CreatedAt
	})

	return items, nil
}
