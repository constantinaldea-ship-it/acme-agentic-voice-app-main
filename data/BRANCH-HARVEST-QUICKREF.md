# Branch Harvest Quick Reference

## One-Command Execution

```bash
# Backup + Harvest + Append (safest)
cp java/bfa-service-resource/src/main/resources/data/branches.json branches.json.backup && \
python harvest_branches.py --missing-cities --append && \
jq 'length' java/bfa-service-resource/src/main/resources/data/branches.json
```

## Verification Commands

```bash
# Count branches
jq 'length' branches.json

# List all cities
jq -r '.[] | .city' branches.json | sort -u

# Find cities with most branches
jq -r '.[] | .city' branches.json | sort | uniq -c | sort -rn | head -20

# Check schema compliance
jq '.[0] | keys | sort' branches.json

# Verify no missing coordinates
jq '.[] | select(.latitude == null or .longitude == null) | .name' branches.json

# Check for empty branchIds
jq '.[] | select(.branchId == "") | .name' branches.json
```

## Missing Cities List

```
Karlsruhe, Wiesbaden, Münster, Augsburg, Aachen,
Mönchengladbach, Braunschweig, Chemnitz, Magdeburg,
Mainz, Lübeck, Erfurt, Saarbrücken, Potsdam,
Heidelberg, Ulm, Würzburg, Regensburg, Ingolstadt
```

## Files

| File | Description |
|------|-------------|
| `harvest_branches.py` | Main executable script |
| `docs/data/HARVEST-SUMMARY.md` | Executive summary |
| `docs/data/harvest-script-documentation.md` | Full documentation |
| `docs/data/missing-cities-analysis.md` | Gap analysis |
| `docs/data/deutsche-bank-branch-harvest.md` | Original harvest methodology |

## Test Verification

✅ Schema: 15/15 fields match  
✅ Types: All types match exactly  
✅ Test run: Karlsruhe (3 branches) successful  
✅ Opening hours: Properly parsed and formatted  

## Support

For issues or questions, refer to:
- `docs/data/harvest-script-documentation.md` (troubleshooting section)
- Original harvest doc: `docs/data/deutsche-bank-branch-harvest.md`
