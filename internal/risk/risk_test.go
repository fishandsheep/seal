package risk

import (
	"strings"
	"testing"
)

func TestRedactionAndFingerprint(t *testing.T) {
	a := Analyze("SELECT * FROM users WHERE email='alice@example.com' AND age=42")
	b := Analyze("select * from users where email='bob@example.com' and age=99")
	if strings.Contains(a.Redacted, "alice") || strings.Contains(a.Redacted, "42") {
		t.Fatalf("not redacted: %s", a.Redacted)
	}
	if a.Fingerprint != b.Fingerprint {
		t.Fatalf("fingerprints differ: %s %s", a.Fingerprint, b.Fingerprint)
	}
}

func TestRiskRulesAndScoreDirection(t *testing.T) {
	critical := Analyze("DROP TABLE users; SELECT SLEEP(10)")
	if critical.Score != 100 {
		t.Fatalf("score=%d findings=%v", critical.Score, critical.Findings)
	}
	safe := Analyze("SELECT id FROM users WHERE id = 1 LIMIT 1")
	if safe.Score != 0 {
		t.Fatalf("safe score=%d findings=%v", safe.Score, safe.Findings)
	}
	write := Analyze("DELETE FROM users")
	if write.Score < 40 {
		t.Fatalf("write score=%d", write.Score)
	}
}

func TestCommentsAndQuotedSemicolon(t *testing.T) {
	r := Analyze("SELECT 'not;multi' /* secret */ FROM t WHERE id=7")
	for _, f := range r.Findings {
		if f.Rule == "multiple-statements" {
			t.Fatal("semicolon in literal treated as statement")
		}
	}
}

func TestSingleTrailingSemicolonIsNotMultiStatement(t *testing.T) {
	r := Analyze("SELECT id FROM users WHERE id=7;")
	for _, f := range r.Findings {
		if f.Rule == "multiple-statements" {
			t.Fatal("single trailing semicolon treated as multi-statement")
		}
	}
	if ok, reason := SafeToExplain("SELECT 1;"); !ok {
		t.Fatalf("safe explain rejected: %s", reason)
	}
}
