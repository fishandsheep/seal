package risk

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"regexp"
	"strings"
	"unicode"
)

type Finding struct {
	Rule       string `json:"rule"`
	Severity   string `json:"severity"`
	Weight     int    `json:"weight"`
	Evidence   string `json:"evidence"`
	Suggestion string `json:"suggestion"`
}

type Report struct {
	Score       int       `json:"score"`
	Redacted    string    `json:"redacted"`
	Fingerprint string    `json:"fingerprint"`
	Findings    []Finding `json:"findings"`
}

var (
	writeNoWhere = regexp.MustCompile(`(?i)^\s*(update|delete)\b(?s:.*)$`)
	dangerDDL    = regexp.MustCompile(`(?i)\b(drop\s+(database|table)|truncate\s+table|alter\s+table\b.*\bdrop\b)\b`)
	dangerFunc   = regexp.MustCompile(`(?i)\b(sleep|benchmark|load_file)\s*\(|\binto\s+(outfile|dumpfile)\b`)
	selectStar   = regexp.MustCompile(`(?i)^\s*select\s+(distinct\s+)?(\w+\.)?\*`)
	leadingLike  = regexp.MustCompile(`(?i)\blike\s+\?`)
	nonSarg      = regexp.MustCompile(`(?i)\b(lower|upper|date|year|month|substring|cast)\s*\([^)]*\)\s*(=|>|<|like|in)`)
	crossJoin    = regexp.MustCompile(`(?i)\bcross\s+join\b|\bjoin\s+[\w.` + "`" + `]+(?:\s+\w+)?\s*(,|where|group|order|limit|$)`)
	selectStart  = regexp.MustCompile(`(?i)^\s*(with\b(?s:.*)\bselect|select)\b`)
)

func Analyze(sql string) Report {
	redacted, multi, leadingWildcard := normalize(sql)
	sum := sha256.Sum256([]byte(redacted))
	r := Report{Redacted: redacted, Fingerprint: hex.EncodeToString(sum[:12])}
	add := func(rule, severity string, weight int, evidence, suggestion string) {
		r.Findings = append(r.Findings, Finding{Rule: rule, Severity: severity, Weight: weight, Evidence: evidence, Suggestion: suggestion})
		r.Score += weight
		if r.Score > 100 {
			r.Score = 100
		}
	}
	lower := strings.ToLower(redacted)
	if writeNoWhere.MatchString(redacted) && !regexp.MustCompile(`(?i)\bwhere\b`).MatchString(redacted) {
		add("write-without-where", "high", 40, "UPDATE/DELETE has no WHERE clause", "Add a selective WHERE clause and verify affected rows in a transaction.")
	}
	if dangerDDL.MatchString(redacted) {
		add("destructive-ddl", "critical", 70, "Destructive DDL token detected", "Use a reviewed migration with backup and rollback plan.")
	}
	if multi {
		add("multiple-statements", "high", 30, "More than one SQL statement detected", "Send and review statements independently; disable multi-statements on clients.")
	}
	if dangerFunc.MatchString(redacted) {
		add("dangerous-function-or-export", "critical", 55, "Server-side delay, file read, or export construct detected", "Remove dangerous construct and restrict FILE privilege.")
	}
	if selectStar.MatchString(redacted) {
		add("select-star", "low", 10, "Projection uses *", "Select only columns required by the caller.")
	}
	if leadingWildcard || leadingLike.MatchString(redacted) && strings.Contains(sql, "%") {
		add("leading-wildcard", "medium", 15, "LIKE pattern starts with a wildcard", "Use a searchable prefix or a dedicated full-text index.")
	}
	if nonSarg.MatchString(redacted) {
		add("non-sargable-predicate", "medium", 15, "Indexed candidate is wrapped in a function", "Move transformation outside the predicate or add an appropriate generated-column index.")
	}
	if crossJoin.MatchString(redacted) {
		add("cartesian-join", "high", 30, "Join has no visible ON/USING predicate or uses CROSS JOIN", "Add explicit join keys and verify estimated row multiplication.")
	}
	if selectStart.MatchString(redacted) && !strings.Contains(lower, " limit ") && !strings.HasSuffix(lower, " limit ?") && !strings.Contains(lower, " where ") {
		add("unbounded-read", "medium", 15, "SELECT has neither WHERE nor LIMIT", "Add a selective predicate or a bounded LIMIT.")
	}
	return r
}

func SafeToExplain(redacted string) (bool, string) {
	s := strings.TrimSpace(strings.ToLower(redacted))
	s = strings.TrimSuffix(s, ";")
	if strings.Contains(s, ";") {
		return false, "multi-statement SQL cannot be explained"
	}
	if !(strings.HasPrefix(s, "select ") || strings.HasPrefix(s, "with ")) {
		return false, "only SELECT and CTE statements can be explained"
	}
	if dangerDDL.MatchString(s) || dangerFunc.MatchString(s) {
		return false, "unsafe SQL construct detected"
	}
	if strings.Contains(s, "?") {
		return false, "original values expired or were redacted"
	}
	return true, ""
}

func normalize(sql string) (string, bool, bool) {
	var out bytes.Buffer
	multi, leadingWildcard, separator := false, false, false
	space := true
	for i := 0; i < len(sql); {
		c := sql[i]
		if unicode.IsSpace(rune(c)) {
			space = true
			i++
			continue
		}
		if c == '#' || (c == '-' && i+1 < len(sql) && sql[i+1] == '-' && (i+2 == len(sql) || unicode.IsSpace(rune(sql[i+2])))) {
			for i < len(sql) && sql[i] != '\n' {
				i++
			}
			space = true
			continue
		}
		if c == '/' && i+1 < len(sql) && sql[i+1] == '*' {
			i += 2
			for i+1 < len(sql) && !(sql[i] == '*' && sql[i+1] == '/') {
				i++
			}
			if i+1 < len(sql) {
				i += 2
			}
			space = true
			continue
		}
		if c == '\'' || c == '"' {
			if separator {
				multi = true
			}
			quote := c
			start := i
			i++
			for i < len(sql) {
				if sql[i] == '\\' {
					i += 2
					continue
				}
				if sql[i] == quote {
					if i+1 < len(sql) && sql[i+1] == quote {
						i += 2
						continue
					}
					i++
					break
				}
				i++
			}
			literal := sql[start:i]
			if len(literal) > 1 && (literal[1] == '%' || (len(literal) > 2 && literal[1] == '\\' && literal[2] == '%')) {
				leadingWildcard = true
			}
			writeToken(&out, "?", &space)
			continue
		}
		if c == '`' {
			if separator {
				multi = true
			}
			start := i
			i++
			for i < len(sql) {
				if sql[i] == '`' {
					i++
					break
				}
				i++
			}
			writeToken(&out, strings.ToLower(sql[start:i]), &space)
			continue
		}
		if isDigit(c) || (c == '.' && i+1 < len(sql) && isDigit(sql[i+1])) {
			if separator {
				multi = true
			}
			i++
			for i < len(sql) && (isDigit(sql[i]) || strings.ContainsRune("abcdefABCDEFxX._eE+-", rune(sql[i]))) {
				i++
			}
			writeToken(&out, "?", &space)
			continue
		}
		if isWord(c) {
			if separator {
				multi = true
			}
			start := i
			i++
			for i < len(sql) && (isWord(sql[i]) || isDigit(sql[i])) {
				i++
			}
			writeToken(&out, strings.ToLower(sql[start:i]), &space)
			continue
		}
		if c == ';' {
			separator = out.Len() != 0
			i++
			space = true
			continue
		}
		if strings.ContainsRune(",()=<>+-*/.%?", rune(c)) {
			if out.Len() > 0 && out.Bytes()[out.Len()-1] != ' ' && c != ',' && c != ')' && c != '.' {
				out.WriteByte(' ')
			}
			out.WriteByte(c)
			if c != '(' && c != '.' {
				space = true
			} else {
				space = false
			}
			i++
			continue
		}
		i++
	}
	return strings.Join(strings.Fields(out.String()), " "), multi, leadingWildcard
}

func writeToken(out *bytes.Buffer, value string, space *bool) {
	if out.Len() > 0 && *space && out.Bytes()[out.Len()-1] != ' ' {
		out.WriteByte(' ')
	}
	out.WriteString(value)
	*space = true
}

func isDigit(c byte) bool { return c >= '0' && c <= '9' }
func isWord(c byte) bool {
	return c == '_' || c == '$' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= 0x80
}
