package web

import (
	"fmt"
	"net/url"
	"strings"
	"time"

	"github.com/fishandsheep/seal/internal/capture"
	"github.com/fishandsheep/seal/internal/config"
	"github.com/fishandsheep/seal/internal/model"
	"github.com/fishandsheep/seal/internal/risk"
)

type PageData struct {
	Base        string
	CSRF        string
	Active      string
	Title       string
	Message     string
	Error       string
	Capture     capture.CaptureStatus
	Metrics     capture.Metrics
	Events      []model.QueryEvent
	Connections []model.Connection
	Jobs        []model.AnalysisJob
	Aggregates  []model.Aggregate
	Aggregate   model.Aggregate
	Report      risk.Report
	Explain     *model.ExplainResult
	ExplainNote string
	Config      config.Config
	Total       int
}

func navClass(active, item string) string {
	base := "flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors"
	if active == item {
		return base + " bg-primary text-primary-foreground"
	}
	return base + " text-muted-foreground hover:bg-background hover:text-foreground"
}

func fmtTime(t time.Time) string {
	if t.IsZero() {
		return "—"
	}
	return t.Local().Format("01-02 15:04:05")
}
func fmtDuration(d time.Duration) string {
	if d <= 0 {
		return "—"
	}
	if d < time.Millisecond {
		return fmt.Sprintf("%.0f µs", float64(d)/float64(time.Microsecond))
	}
	return fmt.Sprintf("%.1f ms", float64(d)/float64(time.Millisecond))
}
func riskLabel(score int) string {
	switch {
	case score >= 70:
		return "严重"
	case score >= 40:
		return "高"
	case score >= 15:
		return "中"
	case score > 0:
		return "低"
	default:
		return "安全"
	}
}
func riskClass(score int) string {
	switch {
	case score >= 70:
		return "border-transparent bg-red-100 text-red-800"
	case score >= 40:
		return "border-transparent bg-orange-100 text-orange-800"
	case score >= 15:
		return "border-transparent bg-amber-100 text-amber-900"
	default:
		return "border-transparent bg-emerald-100 text-emerald-800"
	}
}
func statusClass(state string) string {
	switch state {
	case "running":
		return "text-emerald-700"
	case "degraded":
		return "text-amber-700"
	default:
		return "text-muted-foreground"
	}
}
func statusLabel(state string) string {
	switch state {
	case "running":
		return "运行中"
	case "starting":
		return "启动中"
	case "degraded":
		return "已降级"
	default:
		return "已停止"
	}
}
func queryURL(base, fingerprint string) string {
	return base + "/queries/" + url.PathEscape(fingerprint) + "/risk"
}
func shortID(id string) string {
	if len(id) > 10 {
		return id[:10]
	}
	return id
}
func portsString(ports []uint16) string {
	values := make([]string, len(ports))
	for i, p := range ports {
		values[i] = fmt.Sprint(p)
	}
	return strings.Join(values, ", ")
}
