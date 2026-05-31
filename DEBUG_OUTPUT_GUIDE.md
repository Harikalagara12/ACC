# 🔍 DEBUG OUTPUT GUIDE

## ✅ System.out.println Statements Added

I've added comprehensive debug logging to **ALL** fetch methods. When you run the ACC batch, you'll see detailed output in your console/logs.

---

## 📊 **What to Look For in the Output:**

### **1. fetchACCDataForMultipleIndicatorChange (Most Likely Called)**

```
=== DEBUG fetchACCDataForMultipleIndicatorChange START ===
Indicators: [S, R]                    ← What indicators are present?
baseOrCurrentEventData: BASE
Current Part Color Code: TYPE13       ← New color code
Previous Part Color Code: TYPE28      ← Old color code
Indicators contains PART_COLOR_CODE_CHANGE: false  ← Should be false

=== DEBUG: Entered else-if block (line 1018) ===  ← OR else block?
PART_COLOR_CODE_CHANGE NOT in indicators - should NOT add color code to query

=== DEBUG fetchACCDataForMultipleIndicatorChange QUERY ===
SQL: SELECT ... WHERE ... BASE_EVENT_NAME=...
Query contains PART_COLOR_CODE: false  ← MUST be false!
Parameters: {baseEventName=..., partColorCode=...}

=== DEBUG fetchACCDataForMultipleIndicatorChange RESULT ===
Number of ACCs found: 1                ← Should be > 0 if fix works!

=== DEBUG fetchACCDataForMultipleIndicatorChange END ===
Total ACCs returned: 1                 ← Should match ACCs found
=========================================================
```

---

### **2. fetchACCData (May Be Called for Exact Match)**

```
=== DEBUG fetchACCData START ===
typeOfMatch: EXACT_MATCH              ← Or SUPP_CHANGE_MATCH, etc.
baseOrCurrentEventData: BASE
Current Part Color Code: TYPE13
Previous Part Color Code: TYPE28

=== DEBUG fetchACCData QUERY ===
SQL: SELECT ... WHERE ...
Query contains PART_COLOR_CODE: false  ← Should be false
Parameters: {...}

=== DEBUG fetchACCData RESULT ===
Number of ACCs found: 1                ← Check this!

=== DEBUG fetchACCData END ===
Total ACCs returned: 1
=================================
```

---

### **3. fetchACCDataForUnMatched (For Part Added/Dropped)**

```
=== DEBUG fetchACCDataForUnMatched START ===
currentOrBaseEvent: B                 ← B or C
Current Part Color Code: TYPE13
Previous Part Color Code: TYPE28

=== DEBUG fetchACCDataForUnMatched QUERY ===
SQL: SELECT ... WHERE ...
Parameters: {...}

=== DEBUG fetchACCDataForUnMatched RESULT ===
Number of ACCs found: 1

=== DEBUG fetchACCDataForUnMatched END ===
Total ACCs returned: 1
============================================
```

---

### **4. fetchACCDataForProcChangePartAddedDropped**

```
=== DEBUG fetchACCDataForProcChangePartAddedDropped START ===
baseOrCurrentEventData: BASE
Part Color Code: TYPE13

=== DEBUG fetchACCDataForProcChangePartAddedDropped QUERY ===
SQL: SELECT ... WHERE ...
Query contains PART_COLOR_CODE: false  ← MUST be false!
Parameters: {...}

=== DEBUG fetchACCDataForProcChangePartAddedDropped RESULT ===
Number of ACCs found: 1

=== DEBUG fetchACCDataForProcChangePartAddedDropped END ===
Total ACCs returned: 1
=============================================================
```

---

## ✅ **WHAT SUCCESS LOOKS LIKE:**

### **Scenario: Rerunning ACC batch after color code change (TYPE28 → TYPE13)**

```
=== DEBUG fetchACCDataForMultipleIndicatorChange START ===
Indicators: [S, R]                              ← Supplier + Rate change
Current Part Color Code: TYPE13                 ← New value
Previous Part Color Code: TYPE28                ← Old value
Indicators contains PART_COLOR_CODE_CHANGE: false  ← No explicit color indicator

=== DEBUG: Entered else-if block (line 1018) ===  ← ✅ Correct path!
PART_COLOR_CODE_CHANGE NOT in indicators - should NOT add color code to query

=== DEBUG fetchACCDataForMultipleIndicatorChange QUERY ===
Query contains PART_COLOR_CODE: false           ← ✅ NO color code in query!

=== DEBUG fetchACCDataForMultipleIndicatorChange RESULT ===
Number of ACCs found: 1                         ← ✅ Found existing ACC!

=== DEBUG fetchACCDataForMultipleIndicatorChange END ===
Total ACCs returned: 1                          ← ✅ Returns existing ACC
```

**Result:** Existing APPLIED ACC is found → No duplicate created!

---

## ❌ **WHAT FAILURE LOOKS LIKE:**

### **Bad Scenario 1: Query still has PART_COLOR_CODE**

```
=== DEBUG fetchACCDataForMultipleIndicatorChange QUERY ===
Query contains PART_COLOR_CODE: true            ← ❌ BAD! Still in query!

=== DEBUG fetchACCDataForMultipleIndicatorChange RESULT ===
Number of ACCs found: 0                         ← ❌ Can't find existing ACC
```

**Diagnosis:** Code changes not compiled/deployed

---

### **Bad Scenario 2: Wrong code path taken**

```
=== DEBUG fetchACCDataForMultipleIndicatorChange START ===
Indicators: [S, R, P]                           ← Has P (PART_COLOR_CODE_CHANGE)
Indicators contains PART_COLOR_CODE_CHANGE: true

=== DEBUG: Entered else block (line 1032) ===   ← ❌ Wrong path!
PART_COLOR_CODE_CHANGE IS in indicators
```

**Diagnosis:** System is detecting PART_COLOR_CODE_CHANGE indicator when it shouldn't

---

### **Bad Scenario 3: Different method called**

```
=== DEBUG fetchACCData START ===
typeOfMatch: SUPP_CHANGE_MATCH
```

**Diagnosis:** System is calling `fetchACCData` instead of `fetchACCDataForMultipleIndicatorChange`

---

## 📋 **CHECKLIST - What to Send Me:**

When you run the ACC batch, please copy and paste:

### ✅ **1. All debug output**
Look for lines starting with `=== DEBUG`

### ✅ **2. Answer these questions:**

| Question | Answer |
|----------|--------|
| Which fetch method was called? | _________________ |
| What indicators were detected? | _________________ |
| Current color code? | _________________ |
| Previous color code? | _________________ |
| Query contains PART_COLOR_CODE? | YES / NO |
| Number of ACCs found? | _________________ |
| Was duplicate created? | YES / NO |

### ✅ **3. Database check:**

```sql
SELECT 
    PART_NO_CURR, 
    PART_COLOR_CODE, 
    ACC_STATUS, 
    MODIFIED_TSTP 
FROM FCACC1 
WHERE PART_NO_CURR = '87566-HS2 A800'
ORDER BY MODIFIED_TSTP DESC;
```

---

## 🚀 **NEXT STEPS:**

1. **Compile and deploy** the updated code
2. **Run ACC batch**
3. **Copy console output** (all `=== DEBUG` lines)
4. **Send me the output**
5. **I'll tell you exactly what's wrong!**

---

**With this logging, we'll see EXACTLY where the issue is!** 🔍
