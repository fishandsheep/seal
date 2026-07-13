package model

import "time"

type Connection struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	Host      string    `json:"host"`
	Port      uint16    `json:"port"`
	User      string    `json:"user"`
	Database  string    `json:"database"`
	Endpoint  string    `json:"endpoint"`
	CreatedAt time.Time `json:"created_at"`
}

type ConnectionSecret struct {
	Connection
	Password string `json:"-"`
}

type QueryEvent struct {
	ID            uint64        `json:"id"`
	Time          time.Time     `json:"time"`
	Source        string        `json:"source"`
	Client        string        `json:"client"`
	Server        string        `json:"server"`
	Command       string        `json:"command"`
	SQL           string        `json:"sql"`
	Fingerprint   string        `json:"fingerprint"`
	ParameterMeta string        `json:"parameter_meta,omitempty"`
	Latency       time.Duration `json:"latency"`
	RiskScore     int           `json:"risk_score"`
	BlindReason   string        `json:"blind_reason,omitempty"`
}

type Aggregate struct {
	Fingerprint string        `json:"fingerprint"`
	Sample      string        `json:"sample"`
	Command     string        `json:"command"`
	Count       uint64        `json:"count"`
	Total       time.Duration `json:"total_latency"`
	Max         time.Duration `json:"max_latency"`
	RiskScore   int           `json:"risk_score"`
	FirstSeen   time.Time     `json:"first_seen"`
	LastSeen    time.Time     `json:"last_seen"`
}

func (a Aggregate) Average() time.Duration {
	if a.Count == 0 {
		return 0
	}
	return time.Duration(int64(a.Total) / int64(a.Count))
}

type AnalysisJob struct {
	ID        string    `json:"id"`
	Filename  string    `json:"filename"`
	Status    string    `json:"status"`
	Error     string    `json:"error,omitempty"`
	Packets   uint64    `json:"packets"`
	Queries   uint64    `json:"queries"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

type ExplainResult struct {
	Fingerprint string    `json:"fingerprint"`
	Connection  string    `json:"connection"`
	Schema      string    `json:"schema"`
	Summary     string    `json:"summary"`
	Raw         string    `json:"raw"`
	ExpiresAt   time.Time `json:"expires_at"`
}
