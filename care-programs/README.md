# Care programs

Each `*.yaml` file here defines one care program. The rules-engine reads this folder at the start of
every evaluation, so a new or edited program takes effect on the next run without a redeploy.
A file that fails validation is skipped and reported (`GET /api/v1/programs`); if an earlier valid
version of that file exists, the engine keeps using it.

## Structure

```yaml
id: diabetes-management          # unique, lowercase-with-dashes; results are stored under it
name: Diabetes Management
shortName: DM                    # optional
purpose: One line shown in the API

eligibility: <condition>         # who is in the program

tiers:                           # checked top to bottom; the first match wins
  - id: high-risk
    name: High Risk
    criteria: <condition>        # or `otherwise` (last tier only)
    needs:
      - visit: Endocrinology     # specialty, as spelled in encounters.csv (case-insensitive)
        everyDays: 90
        priority: high           # optional: normal (default) or high
        note: Get labs done      # optional
```

## Conditions

All dates are relative to the evaluation date ("today").

| Condition | Matches when |
|---|---|
| `age: { min: 18, max: 64 }` | Age in whole years is within the bounds (both inclusive, either optional) |
| `diagnosis: { chronic: true }` | Any diagnosis in a chronic condition group (clinical-data's condition groups) |
| `diagnosis: { conditionGroups: [E10, E11] }` | Any diagnosis in one of these condition groups (by ICD prefix) |
| `diagnosis: { icdPrefixes: [E66] }` | Any diagnosis whose ICD code starts with one of these (for codes outside the groups) |
| `lab: { test: HbA1c, withinMonths: 6, latest: { gte: 7.0, lt: 9.0 } }` | The most recent result in the window is in range (`gt`, `gte`, `lt`, `lte`) |
| `lab: { test: HbA1c, withinMonths: 6, exists: false }` | No result in the window (`exists: true` for "has one") |
| `all: [ ... ]` / `any: [ ... ]` / `not: ...` | Combine conditions |

`withinMonths` counts calendar months back from today, inclusive. Test and specialty names are
matched case-insensitively against `labs.csv` and `encounters.csv`.

## Care need status

For each need, the engine finds the patient's last visit to that specialty (on or before today) and
their next booked appointment (after today):

| Status | Meaning |
|---|---|
| `MET` | Last visit + `everyDays` is still in the future |
| `SCHEDULED` | Due today or earlier, but an appointment is booked |
| `OVERDUE` | Due today or earlier, and nothing is booked (never seen counts as due today) |
