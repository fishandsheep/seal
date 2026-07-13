package config

import "testing"

func TestParseRejectsCommandInjectionValues(t *testing.T) {
	for _, args := range [][]string{{"--interface", "any\n-oops"}, {"--mysql-ports", "3306;id"}, {"--tcpdump", "tcpdump\x00x"}} {
		if _, err := Parse(args); err == nil {
			t.Fatalf("Parse(%q) succeeded", args)
		}
	}
}

func TestCLIOverridesEnvironmentDefaults(t *testing.T) {
	t.Setenv("SEAL_MYSQL_PORTS", "3307")
	c, err := Parse([]string{"--mysql-ports", "3308,3309"})
	if err != nil {
		t.Fatal(err)
	}
	if len(c.MySQLPorts) != 2 || c.MySQLPorts[0] != 3308 {
		t.Fatalf("ports = %v", c.MySQLPorts)
	}
}
