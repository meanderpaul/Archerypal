package api

import (
	"testing"

	"github.com/meanderpaul/archerypal-server/internal/store"
)

func TestValidateCompleteMatch(t *testing.T) {
	room := &store.Room{
		HostFriendCode:   "AP-HOST01",
		JoinerFriendCode: "AP-JOIN01",
		Status:           "open",
	}
	req := completeReq{
		MatchID:          "m1",
		TargetCount:      3,
		WinnerFriendCode: "AP-HOST01",
		Players: []store.CompletePlayer{
			{FriendCode: "AP-HOST01", Total: 100},
			{FriendCode: "AP-JOIN01", Total: 90},
		},
	}
	if err := validateCompleteMatch(room, req); err != nil {
		t.Fatalf("expected valid match, got %v", err)
	}
}

func TestValidateCompleteMatch_RejectsUnknownPlayer(t *testing.T) {
	room := &store.Room{
		HostFriendCode:   "AP-HOST01",
		JoinerFriendCode: "AP-JOIN01",
		Status:           "open",
	}
	req := completeReq{
		MatchID:          "m1",
		TargetCount:      3,
		WinnerFriendCode: "AP-HOST01",
		Players: []store.CompletePlayer{
			{FriendCode: "AP-HOST01", Total: 100},
			{FriendCode: "AP-EXTRA1", Total: 90},
		},
	}
	if err := validateCompleteMatch(room, req); err == nil {
		t.Fatal("expected error for unknown player")
	}
}

func TestValidateCompleteMatch_RejectsClosedRoom(t *testing.T) {
	room := &store.Room{
		HostFriendCode: "AP-HOST01",
		Status:         "closed",
	}
	req := completeReq{
		MatchID:          "m1",
		WinnerFriendCode: "AP-HOST01",
		Players:          []store.CompletePlayer{{FriendCode: "AP-HOST01", Total: 1}},
	}
	if err := validateCompleteMatch(room, req); err == nil {
		t.Fatal("expected error for closed room")
	}
}

func TestValidateCompleteMatch_ScoreRange(t *testing.T) {
	room := &store.Room{
		HostFriendCode:   "AP-HOST01",
		JoinerFriendCode: "AP-JOIN01",
		Status:           "open",
	}
	req := completeReq{
		MatchID:          "m1",
		TargetCount:      1,
		WinnerFriendCode: "AP-HOST01",
		Players: []store.CompletePlayer{
			{FriendCode: "AP-HOST01", Total: 9999},
		},
	}
	if err := validateCompleteMatch(room, req); err == nil {
		t.Fatal("expected score range error")
	}
}

func TestValidateCompleteMatch_WinnerMustBeInPlayers(t *testing.T) {
	room := &store.Room{
		HostFriendCode:   "AP-HOST01",
		JoinerFriendCode: "AP-JOIN01",
		Status:           "open",
	}
	req := completeReq{
		MatchID:          "m1",
		TargetCount:      3,
		WinnerFriendCode: "AP-JOIN01",
		Players: []store.CompletePlayer{
			{FriendCode: "AP-HOST01", Total: 100},
		},
	}
	if err := validateCompleteMatch(room, req); err == nil {
		t.Fatal("expected winner validation error")
	}
}
