// Package seal owns repository-wide generation directives.
package seal

//go:generate go run github.com/a-h/templ/cmd/templ@v0.3.1001 generate
//go:generate npm ci --ignore-scripts
//go:generate npx @tailwindcss/cli -i web/assets/css/input.css -o web/assets/css/output.css --minify
//go:generate cp node_modules/htmx.org/dist/htmx.min.js web/assets/js/htmx.min.js
//go:generate cp node_modules/htmx-ext-sse/dist/sse.min.js web/assets/js/sse.min.js
