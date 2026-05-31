# 🔍 DEBUG CHECKLIST - ACC Duplicate Row Issue

## ⚠️ Issue Status: STILL NOT WORKING

You reported the fix is still not working. Let's systematically debug this.

---

## 📋 **QUESTIONS TO ANSWER:**

### 1. **What exactly is happening when you rerun ACC batch?**
   - [ ] Duplicate NON-APPLIED rows are created
   - [ ] Existing APPLIED ACC is not found
   - [ ] Existing APPLIED ACC is found BUT still creates duplicate
   - [ ] Something else: _______________

### 2. **What indicator scenario are you testing?**
   - [ ] Supplier Change only
   - [ ] Share Rate Change only
   - [ ] Part Color Code Change only
   - [ ] **Multiple indicators** (Supplier + Share Rate + Color Code)
   - [ ] Part Added/Dropped

### 3. **What color code change occurred?**
   - From: _______ (e.g., TYPE28)
   - To: _______ (e.g., TYPE13)

### 4. **Database Check - Does existing ACC have the OLD or NEW color code?**
   ```sql
   SELECT PART_NO_CURR, PART_COLOR_CODE, ACC_STATUS, MODIFIED_TSTP
   FROM FCACC1
   WHERE PART_NO_CURR = '87566-HS2 A800'
   ORDER BY MODIFIED_TSTP DESC;
   ```
   Result: _____________

---

## 🔍 **DEBUG STEPS:**

### Step 1: Check if fetchACCDataForMultipleIndicatorChange is being called
Add logging to line 8490 in `ACCProcessingBatchBO (1).java`:
```java
log.info("===> CALLING fetchACCDataForMultipleIndicatorChange with indicators: " + lstIndicators);
m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCDataForMultipleIndicatorChange(...);
log.info("===> RESULT: Found " + (m_lenterACCSuppSummaryACCDataDetailsDTOList != null ? m_lenterACCSuppSummaryACCDataDetailsDTOList.size() : 0) + " ACCs");
```

**Expected:** If fix is working, should find existing ACC (size > 0)  
**Actual:** _____________

---

### Step 2: Check the SQL query being executed
Add logging at line 1080 in `ACCProcessingBatchDAO (1).java`:
```java
log.info("===> fetchACCDataForMultipleIndicatorChange QUERY: " + querySB.toString());
log.info("===> PARAMETERS: " + queryParameters);
results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(querySB.toString()), queryParameters);
log.info("===> SQL RESULT SIZE: " + results.size());
```

**Check the query for:**
- [ ] Does it still have `PART_COLOR_CODE` in WHERE clause? (Should be NO)
- [ ] What are the parameter values?

**Actual Query:** _____________

---

### Step 3: Check which code path is taken
Check line 1018 logic:
```java
}else if(!indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PART_COLOR_CODE_CHANGE.value())){
```

**Questions:**
1. What indicators are in the list? _____________
2. Does it contain "PART_COLOR_CODE_CHANGE"? YES / NO
3. If NO, does it enter the else-if block? _____________

---

### Step 4: Check if the fix was actually compiled and deployed
```bash
# Check file modification time
ls -la ACCProcessingBatchDAO*.java
ls -la ACCProcessingBatchSQLIF.java

# Check if .class files are newer than .java files
ls -la ACCProcessingBatchDAO*.class
ls -la ACCProcessingBatchSQLIF.class
```

**Question:** Are the compiled .class files NEWER than the .java files?  
Answer: _____________

---

## 🎯 **POSSIBLE ROOT CAUSES:**

### Cause 1: Code not compiled/deployed
✅ **Fix:** Recompile and redeploy the application

### Cause 2: Indicator list contains "PART_COLOR_CODE_CHANGE"
If indicators = [S, R, **P**] (Supplier, Rate, Part Color), then:
- Line 1018 condition: `!indicator.contains("P")` = FALSE
- Code enters ELSE block (line 1032) instead of our fixed block
- Result: Different code path!

✅ **Check:** What exact indicators are present? _____________

### Cause 3: Different method is being called
The system might be calling a DIFFERENT method instead of `fetchACCDataForMultipleIndicatorChange`:
- `fetchACCData` (lines 483-635)
- `fetchACCDataForUnMatched` (lines 880-956)  
- `fetchACCDataForProcChangePartAddedDropped` (lines 1543-1665)

✅ **Check:** Add logging to ALL fetch methods to see which one is called

### Cause 4: Empty result triggers new ACC creation
Even if query works correctly:
- If no ACC found → Line 8789 ELSE block executes
- Creates NEW NON-APPLIED ACC

✅ **Check:** Is existing ACC being found? _____________

### Cause 5: Part comparison key is different
The part data might have changed in ways that make it "different":
- Supplier changed
- Proc section changed
- Model/MTC values changed

✅ **Check:** Compare ALL fields between old and new ACC row:
```sql
SELECT SUPPLIER_NO_BASE, SUPPLIER_NO_CURR, PROC_SECT_CODE, PART_SECTION_CODE,
       BASE_TGT_MODEL_DEV_CODE, CURR_TGT_MODEL_DEV_CODE, PART_COLOR_CODE
FROM FCACC1
WHERE PART_NO_CURR = '87566-HS2 A800';
```

---

## 💡 **IMMEDIATE ACTIONS:**

### Action 1: Enable Detailed Logging
Add these log statements:

**In ACCProcessingBatchBO (1).java line 8490:**
```java
log.info("===DEBUG=== Multiple Indicators: " + lstIndicators + " | Current Color: " + 
         currentEventPartDetails.getM_strPartColorCode() + " | Previous Color: " + 
         previousEventPartDetails.getM_strPartColorCode());
```

**In ACCProcessingBatchDAO (1).java line 1018:**
```java
log.info("===DEBUG=== Indicators contains PART_COLOR_CODE_CHANGE? " + 
         indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PART_COLOR_CODE_CHANGE.value()));
log.info("===DEBUG=== Entering else-if block (line 1018)");
```

**In ACCProcessingBatchDAO (1).java line 1080:**
```java
log.info("===DEBUG=== Final Query: " + querySB.toString());
log.info("===DEBUG=== Query contains PART_COLOR_CODE in WHERE? " + 
         querySB.toString().contains("PART_COLOR_CODE"));
```

### Action 2: Check Database State
```sql
-- Check existing ACC
SELECT 
    PART_NO_BASE, 
    PART_NO_CURR, 
    PART_COLOR_CODE, 
    ACC_STATUS, 
    SUPPLIER_NO_BASE,
    SUPPLIER_NO_CURR,
    BASE_EVENT_NAME,
    CURR_EVENT_NAME,
    MODIFIED_TSTP
FROM FCACC1
WHERE PART_NO_CURR LIKE '%87566%'
ORDER BY MODIFIED_TSTP DESC;
```

### Action 3: Verify Fix Was Applied
Check the actual code in deployed environment:
```bash
grep -A 5 "else if(!indicator.contains" ACCProcessingBatchDAO*.java
```

Expected: Should see commented out code (lines 1016-1024)

---

## 📊 **EXPECTED vs ACTUAL:**

| Check | Expected | Actual | Status |
|-------|----------|--------|--------|
| Query contains PART_COLOR_CODE | NO | ? | ? |
| fetchACCDataForMultipleIndicatorChange called | YES | ? | ? |
| Existing ACC found | YES | ? | ? |
| New row created | NO | ? | ? |

---

## 🚨 **NEXT STEPS:**

1. **Enable all logging** above
2. **Run ACC batch**
3. **Check logs** for debug statements
4. **Report back:**
   - What indicators were detected?
   - Was PART_COLOR_CODE in query?
   - How many ACCs were found?
   - Was new row created?

---

**Please fill in the answers and share the log output!** 🔍
