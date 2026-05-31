# Before and After Comparison

## The Issue in Visual Form

### ❌ BEFORE FIX:

```
RUN 1:
------
Part: 87566-HS2 A800
Supplier: 582804 (NIPPON CARBIDE)
Color Code: TYPE13
Share Rate: 0.0000
Status: ACC Created → APPLIED ✓

↓ (Color Code Changed to TYPE14)

RUN 2:
------
Part: 87566-HS2 A800
Supplier: 582804 (NIPPON CARBIDE)  
Color Code: TYPE14 (CHANGED!)
Share Rate: 0.0000

Query: SELECT * FROM FCACC1 WHERE ... AND PART_COLOR_CODE = 'TYPE14'
Result: NO MATCH FOUND ❌
Action: CREATE NEW ACC → NON-APPLIED ❌

Database now has:
┌─────────────────────────────────────────────────────┐
│ Row 1: Color=TYPE13 | Status=APPLIED                │ ← Old ACC (orphaned)
│ Row 2: Color=TYPE14 | Status=NON-APPLIED            │ ← New ACC (duplicate!)
└─────────────────────────────────────────────────────┘
```

### ✅ AFTER FIX:

```
RUN 1:
------
Part: 87566-HS2 A800
Supplier: 582804 (NIPPON CARBIDE)
Color Code: TYPE13
Share Rate: 0.0000
Status: ACC Created → APPLIED ✓

↓ (Color Code Changed to TYPE14)

RUN 2:
------
Part: 87566-HS2 A800
Supplier: 582804 (NIPPON CARBIDE)
Color Code: TYPE14 (CHANGED!)
Share Rate: 0.0000

Query: SELECT * FROM FCACC1 WHERE ... (NO PART_COLOR_CODE CHECK!)
Result: MATCH FOUND ✓ (Found Row 1)
Action: REUSE EXISTING ACC → KEEP AS APPLIED ✓

Database now has:
┌─────────────────────────────────────────────────────┐
│ Row 1: Color=TYPE14 | Status=APPLIED                │ ← Updated with new color
└─────────────────────────────────────────────────────┘
```

---

## SQL Query Changes

### ❌ BEFORE (Line 277):
```sql
ENTER_ACC_SUPP_MTO_SUMMARY_PROC_CHANGE_ADDED_DROPPED_PARTS_ACC_DATA = 
  " SELECT ACC.RULE_ID, ACC.APP_COST_CHANGE_CODE, ... " +
  " FROM --CART_SCHEMA--.FCACC1 ACC " +
  " WHERE " +
  " ACC.BASE_EVENT_NAME=:baseEventName AND ... " +
  " AND ACC.PART_NO_CURR=:partNumberCurrent " +
  " AND ACC.SUPPLIER_NO_BASE=:baseSupplierNumber " +
  " AND ACC.PROC_SECT_CODE=:procSectCode " +
  " AND COALESCE(TRIM(ACC.PART_COLOR_CODE), '') = :partColorCode " +  ← ❌ REMOVED THIS
  " AND BASE_TGT_MODEL_DEV_CODE=:baseTgtModelDevCode " +
  " ... "
```

### ✅ AFTER (Line 277):
```sql
ENTER_ACC_SUPP_MTO_SUMMARY_PROC_CHANGE_ADDED_DROPPED_PARTS_ACC_DATA = 
  " SELECT ACC.RULE_ID, ACC.APP_COST_CHANGE_CODE, ... " +
  " FROM --CART_SCHEMA--.FCACC1 ACC " +
  " WHERE " +
  " ACC.BASE_EVENT_NAME=:baseEventName AND ... " +
  " AND ACC.PART_NO_CURR=:partNumberCurrent " +
  " AND ACC.SUPPLIER_NO_BASE=:baseSupplierNumber " +
  " AND ACC.PROC_SECT_CODE=:procSectCode " +
  " AND BASE_TGT_MODEL_DEV_CODE=:baseTgtModelDevCode " +  ← ✅ No color code check!
  " ... "
```

---

## Why This Matters

### Problem Scenario from Screenshot:
Looking at your screenshot (Part Level Details):
- **Part:** 87566-HS2 A800  
- **Supplier:** 582804 NIPPON CARBIDE INDUSTRIES
- **Indicator:** "Supplier Change"
- **Current Cost:** 0.0000
- **MCC:** 0.0000
- **Balance:** 0.2600

**What was happening:**
1. First run: ACC created for this part with old color code → APPLIED
2. Color code changed (or supplier/share rate changed)
3. Second run: System couldn't find existing ACC (because color code didn't match)
4. Result: Created NEW ACC as NON-APPLIED → Balance shows 0.2600 again!

**What should happen (after fix):**
1. First run: ACC created → APPLIED
2. Color code changed
3. Second run: System FINDS existing ACC (ignoring color code in lookup)
4. Result: Existing APPLIED ACC is maintained → No duplicate rows!

---

## Key Principle

**Part Color Code should be:**
- ✅ **Tracked**: Store it in the ACC record
- ✅ **Detected**: Recognize when it changes
- ❌ **Not a Lookup Key**: Don't use it to find existing ACCs

**Same applies to:**
- ✅ Supplier changes
- ✅ Share rate changes  
- ✅ Part color code changes

All these changes should **UPDATE** existing ACC, not create duplicates!

---

## Testing Checklist

- [ ] Run ACC batch with initial data
- [ ] Apply the ACC
- [ ] Change part color code in source data
- [ ] Re-run ACC batch
- [ ] **Expected Result:** Existing APPLIED ACC is found and maintained (no new NON-APPLIED row)

- [ ] Repeat test with supplier change
- [ ] Repeat test with share rate change
- [ ] Repeat test with combination of changes

