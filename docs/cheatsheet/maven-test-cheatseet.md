mvn -q test \
-Dtest=AccountBalanceServiceTest \
-Dsurefire.printSummary=true \
-Dsurefire.trimStackTrace=false \
-Dsurefire.reportFormat=plain \
-Dsurefire.useFile=false \
| grep -E "Running|Tests run:|Failures:|AssertionFailedError|Verification failed|Expected|Actual" \
| tee test-failures.log


simple example
[ERROR] Tests run: 7, Failures: 5, Errors: 0, Skipped: 0, Time elapsed: 2.071 s <<< FAILURE! -- in com.dogancaglar.paymentservice.application.usecases.AccountBalanceServiceTest
org.opentest4j.AssertionFailedError: expected: <[7]> but was: <[7, 7, 7, 7]>
org.opentest4j.AssertionFailedError: expected: <[15]> but was: <[15, 15, 15, 15]>
org.opentest4j.AssertionFailedError: Expected value to be true.
Verification failed: call 1 of 4: AccountBalanceCachePort(#17).addDeltaAndWatermark(eq(AUTH_RECEIVABLE.GLOBAL), eq(-1500), eq(21))). No matching calls found.
org.opentest4j.AssertionFailedError: expected: <[12]> but was: <[12, 12, 12, 12]>
[ERROR] Failures:
[ERROR]   AccountBalanceServiceTest.should group by multiple accounts and compute independent deltas:165 Verification failed: call 1 of 4: AccountBalanceCachePort(#17).addDeltaAndWatermark(eq(AUTH_RECEIVABLE.GLOBAL), eq(-1500), eq(21))). No matching calls found.
[ERROR]   AccountBalanceServiceTest.should skip accounts whose total delta resolves to zero:192 Expected value to be true.
[ERROR] Tests run: 7, Failures: 5, Errors: 0, Skipped: 0
