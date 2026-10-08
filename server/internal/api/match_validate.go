package api

import (
	"errors"
	"fmt"

	"github.com/meanderpaul/archerypal-server/internal/store"
)

func validateCompleteMatch(dbRoom *store.Room, req completeReq) error {
	if dbRoom.Status == "closed" {
		return errors.New("room already closed")
	}
	allowed := map[string]struct{}{
		dbRoom.HostFriendCode: {},
	}
	if dbRoom.JoinerFriendCode != "" {
		allowed[dbRoom.JoinerFriendCode] = struct{}{}
	}
	winnerOK := false
	for _, p := range req.Players {
		if _, ok := allowed[p.FriendCode]; !ok {
			return fmt.Errorf("player not in room: %s", p.FriendCode)
		}
		if p.FriendCode == req.WinnerFriendCode {
			winnerOK = true
		}
		if p.Total < 0 {
			return errors.New("negative score")
		}
		if p.Total > 10000 {
			return errors.New("score out of range")
		}
		if req.TargetCount > 0 && p.Total > req.TargetCount*600 {
			return errors.New("score out of range")
		}
	}
	if !winnerOK {
		return errors.New("winner not in players")
	}
	return nil
}
