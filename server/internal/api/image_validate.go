package api

import (
	"bytes"
	"io"
)

func sniffImageExt(r io.Reader) (ext string, rest io.Reader, ok bool) {
	buf := make([]byte, 512)
	n, err := io.ReadAtLeast(r, buf, 8)
	if err != nil && err != io.EOF && err != io.ErrUnexpectedEOF {
		return "", r, false
	}
	head := buf[:n]
	switch {
	case len(head) >= 3 && head[0] == 0xFF && head[1] == 0xD8 && head[2] == 0xFF:
		return ".jpg", io.MultiReader(bytes.NewReader(head), r), true
	case len(head) >= 8 && head[0] == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47:
		return ".png", io.MultiReader(bytes.NewReader(head), r), true
	default:
		return "", io.MultiReader(bytes.NewReader(head), r), false
	}
}
