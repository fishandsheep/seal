package assets

import "embed"

// Files contains all browser assets required at runtime.
//
//go:embed css/output.css js/*.min.js img/*
var Files embed.FS
