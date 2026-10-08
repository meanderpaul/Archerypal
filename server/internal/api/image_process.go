package api

import (
	"bytes"
	"fmt"
	"image"
	"image/jpeg"
	_ "image/png"
	"io"
)

const maxImageEdgePx = 1920

// sanitizePostImage decodes JPEG/PNG, caps dimensions, and re-encodes as JPEG
// (strips EXIF and rejects undecodable / polyglot payloads).
func sanitizePostImage(r io.Reader) ([]byte, error) {
	limited := io.LimitReader(r, maxPostImageBytes)
	img, _, err := image.Decode(limited)
	if err != nil {
		return nil, fmt.Errorf("decode: %w", err)
	}
	img = scaleDownMaxEdge(img, maxImageEdgePx)

	var out bytes.Buffer
	if err := jpeg.Encode(&out, img, &jpeg.Options{Quality: 85}); err != nil {
		return nil, fmt.Errorf("encode: %w", err)
	}
	if out.Len() == 0 || out.Len() > maxPostImageBytes {
		return nil, fmt.Errorf("encoded image too large")
	}
	return out.Bytes(), nil
}

func scaleDownMaxEdge(src image.Image, maxEdge int) image.Image {
	b := src.Bounds()
	w, h := b.Dx(), b.Dy()
	if w <= 0 || h <= 0 {
		return src
	}
	longest := w
	if h > longest {
		longest = h
	}
	if longest <= maxEdge {
		return src
	}
	nw := w * maxEdge / longest
	nh := h * maxEdge / longest
	if nw < 1 {
		nw = 1
	}
	if nh < 1 {
		nh = 1
	}
	dst := image.NewRGBA(image.Rect(0, 0, nw, nh))
	for y := 0; y < nh; y++ {
		sy := b.Min.Y + y*h/nh
		for x := 0; x < nw; x++ {
			sx := b.Min.X + x*w/nw
			dst.Set(x, y, src.At(sx, sy))
		}
	}
	return dst
}
