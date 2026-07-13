package database

import (
	"strings"
	"testing"
)

func TestSummarizeExplain(t *testing.T) {
	raw := `{"query_block":{"table":{"access_type":"ALL","rows_examined_per_scan":4200,"possible_keys":["idx_user"],"using_temporary_table":true,"using_filesort":true}}}`
	s, err := summarize(raw)
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"full table scan", "4200", "temporary table", "filesort", "idx_user"} {
		if !strings.Contains(s, want) {
			t.Fatalf("summary %q missing %q", s, want)
		}
	}
}

func TestSanitizeExplainConditions(t *testing.T) {
	raw := `{"query_block":{"table":{"attached_condition":"users.email = 'alice@example.com' and users.id = 42","key":"idx_email"}}}`
	got, err := sanitizeExplain(raw)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(got, "alice") || strings.Contains(got, "42") {
		t.Fatalf("sensitive condition persisted: %s", got)
	}
	if !strings.Contains(got, "idx_email") {
		t.Fatalf("plan metadata lost: %s", got)
	}
}
