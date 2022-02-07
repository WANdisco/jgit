package org.eclipse.jgit.lib;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public class RefUpdateResultTest {
    public static boolean isEqualIgnoringOrder(List<String> expected, List<String> actual) {
        if (expected == null) {
            return actual == null;
        }

        if (expected.size() != actual.size()) {
            return false;
        }

        expected = expected.stream().sorted().collect(Collectors.toList());
        actual = actual.stream().sorted().collect(Collectors.toList());

        return expected.equals(actual);
    }

    // Test that expects the known set of enums (as of stable-5.1-WD) to match
    // the set currently found in the Result enum within the RefUpdate class. If they do
    // not match then it means that enums have been added or removed. This test will alert to
    // which enums have been added or removed if that is the case.
    @Test
    public void checkRefUpdateResultEnums(){

        //valid list as of jgit stable-5.1-WD
        List<String> expectedEnumValues = new ArrayList<>(
                Arrays.asList("NOT_ATTEMPTED", "LOCK_FAILURE", "NO_CHANGE",
                        "NEW", "FORCED", "FAST_FORWARD", "REJECTED", "REJECTED_CURRENT_BRANCH",
                        "IO_FAILURE", "RENAMED", "REJECTED_MISSING_OBJECT", "REJECTED_OTHER_REASON"));

        List<String> actualEnumValues = Arrays.stream(RefUpdate.Result.values())
                .map(Enum::toString)
                .collect(Collectors.toList());

        StringBuilder errMsg = new StringBuilder();
        if(!isEqualIgnoringOrder(expectedEnumValues, actualEnumValues)){
            Set<String> missing = new HashSet<>(expectedEnumValues);
            missing.removeAll(actualEnumValues);
            if(!missing.isEmpty()) {
                errMsg.append(String.format(
                        "There are Enums in our expected set that do not appear in " +
                                "RefUpdate.Result actual set %s. Have Enums have been removed?", missing));
            }

            Set<String> extra = new HashSet<>(actualEnumValues);
            extra.removeAll(expectedEnumValues);
            if(!extra.isEmpty()) {
                errMsg.append(String.format("There are missing Enums in our expected set of enums " +
                        "that appear in RefUpdate.Result actual set %s. Have New Enums have been added?", extra));
            }
        }

        assertTrue(String.format("The actual set of RefUpdate.Result Enums does not match the expected set. %s", errMsg),
                isEqualIgnoringOrder(expectedEnumValues, actualEnumValues));
    }
}
