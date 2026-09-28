package me.shail.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProgramParserTest {

    static final String VALID = """
            id: sample-program
            name: Sample Program
            eligibility:
              all:
                - age: { min: 18, max: 64 }
                - not: { diagnosis: { icdPrefixes: [Z00] } }
            tiers:
              - id: risky
                name: Risky
                criteria:
                  lab: { test: HbA1c, withinMonths: 6, latest: { gte: 9.0 } }
                needs:
                  - { visit: Endocrinology, everyDays: 90, priority: high, note: Call first }
              - id: rest
                name: Rest
                criteria: otherwise
                needs:
                  - { visit: PCP, everyDays: 365 }
            """;

    @Test
    void parsesEveryPart() {
        ProgramDefinition program = ProgramParser.parse(VALID);

        assertEquals("sample-program", program.id());
        assertEquals("Sample Program", program.name());
        assertNull(program.shortName(), "shortName is optional");
        Condition.All eligibility = assertInstanceOf(Condition.All.class, program.eligibility());
        assertEquals(new Condition.Age(18, 64), eligibility.conditions().get(0));
        assertEquals(new Condition.Not(new Condition.Diagnosis(null, null, List.of("Z00"))), eligibility.conditions().get(1));

        ProgramDefinition.Tier risky = program.tiers().get(0);
        assertEquals(new Condition.Lab("HbA1c", 6, new Condition.Range(null, new BigDecimal("9"), null, null), null),
                risky.criteria());
        assertEquals(new ProgramDefinition.Need("Endocrinology", 90, "high", "Call first"), risky.needs().get(0));

        ProgramDefinition.Tier rest = program.tiers().get(1);
        assertInstanceOf(Condition.Otherwise.class, rest.criteria());
        assertEquals(new ProgramDefinition.Need("PCP", 365, "normal", null), rest.needs().get(0), "priority defaults to normal");
    }

    @Test
    void reportsEveryProblemWithItsLocation() {
        ProgramDefinitionException e = assertThrows(ProgramDefinitionException.class, () -> ProgramParser.parse("""
                id: Bad Id
                name: Broken
                colour: blue
                eligibility:
                  age: { min: 70, max: 60 }
                tiers:
                  - id: first
                    name: First
                    criteria: otherwise
                    needs:
                      - { visit: PCP, everyDays: 0 }
                  - id: first
                    name: Second
                    criteria:
                      lab: { test: HbA1c, withinMonth: 6, latest: {} }
                    needs:
                      - { visit: PCP, everyDays: 30, priority: urgent }
                      - { visit: pcp, everyDays: 60 }
                """));

        assertEquals(List.of(
                "unknown key 'colour'",
                "id: 'Bad Id' must be lowercase letters, digits and dashes (e.g. diabetes-management)",
                "eligibility.age: min 70 is greater than max 60",
                "tiers[0].criteria: 'otherwise' is only allowed on the last tier",
                "tiers[0].needs[0].everyDays: expected a whole number greater than 0",
                "tiers[1].id: duplicate tier id 'first'",
                "tiers[1].criteria.lab: unknown key 'withinMonth'",
                "tiers[1].criteria.lab.latest: set at least one of gt, gte, lt, lte",
                "tiers[1].needs[0].priority: expected normal or high, got 'urgent'",
                "tiers[1].needs[1].visit: 'pcp' is listed twice in this tier"), e.errors());
    }

    @Test
    void rejectsUnknownConditionsAndMissingFields() {
        ProgramDefinitionException e = assertThrows(ProgramDefinitionException.class, () -> ProgramParser.parse("""
                id: p
                eligibility:
                  gender: F
                tiers: []
                """));

        assertTrue(e.errors().contains("name: required"), e.errors().toString());
        assertTrue(e.errors().contains("eligibility: unknown condition 'gender' (expected all, any, not, age, diagnosis or lab)"),
                e.errors().toString());
        assertTrue(e.errors().contains("tiers: expected a non-empty list"), e.errors().toString());
    }

    @Test
    void rejectsInvalidYaml() {
        ProgramDefinitionException e = assertThrows(ProgramDefinitionException.class,
                () -> ProgramParser.parse("id: [unclosed"));

        assertTrue(e.errors().getFirst().startsWith("not valid YAML"), e.errors().toString());
    }

    @Test
    void labNeedsARangeOrExists() {
        ProgramDefinitionException e = assertThrows(ProgramDefinitionException.class, () -> ProgramParser.parse("""
                id: p
                name: P
                eligibility: { lab: { test: HbA1c } }
                tiers:
                  - { id: t, name: T, criteria: otherwise, needs: [ { visit: PCP, everyDays: 1 } ] }
                """));

        assertEquals(List.of("eligibility.lab: set latest (a value range) or exists"), e.errors());
    }
}
