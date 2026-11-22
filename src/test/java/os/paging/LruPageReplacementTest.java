package os.paging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Regression tests for frame allocation, LRU eviction, and immutable reports. */
class LruPageReplacementTest {

    @Test
    void originalExerciseHasSixFaultsAndFourHits() {
        SimulationResult result = new LruPageReplacement(4)
                .simulate(Arrays.asList(2, 2, 1, 4, 5, 2, 5, 6, 5, 7));

        assertEquals(10, result.getRequestCount());
        assertEquals(4, result.getFrameCount());
        assertEquals(4, result.getHitCount());
        assertEquals(6, result.getFaultCount());
        assertEquals(0.6, result.getFaultRate(), 1e-10);
        assertEquals(Arrays.asList(2, 6, 7, 5), result.getFinalFrames());
        assertEquals(PageAccess.Outcome.FAULT_REPLACE, result.getAccesses().get(7).getOutcome());
        assertEquals(Integer.valueOf(1), result.getAccesses().get(7).getEvictedPage());
        assertEquals(Integer.valueOf(4), result.getAccesses().get(9).getEvictedPage());
    }

    @Test
    void duplicateReferencesWhileFramesAreEmptyAreHitsNotLoads() {
        SimulationResult result = new LruPageReplacement(4).simulate(Arrays.asList(3, 3, 3));
        assertEquals(1, result.getFaultCount());
        assertEquals(2, result.getHitCount());
        assertEquals(Arrays.asList(3, null, null, null), result.getFinalFrames());
        assertEquals(PageAccess.Outcome.HIT, result.getAccesses().get(1).getOutcome());
    }

    @Test
    void lruRecencyIsUpdatedWhenAPageIsHit() {
        SimulationResult result = new LruPageReplacement(3).simulate(Arrays.asList(1, 2, 3, 1, 4));
        assertEquals(Integer.valueOf(2), result.getAccesses().get(4).getEvictedPage());
        assertEquals(Arrays.asList(1, 4, 3), result.getFinalFrames());
    }

    @Test
    void capacityOneEvictsPreviousPageOnEveryMiss() {
        SimulationResult result = new LruPageReplacement(1).simulate(Arrays.asList(1, 2, 2, 3));
        assertEquals(3, result.getFaultCount());
        assertEquals(1, result.getHitCount());
        assertEquals(Integer.valueOf(2), result.getAccesses().get(3).getEvictedPage());
        assertEquals(Collections.singletonList(3), result.getFinalFrames());
    }

    @Test
    void surplusFramesRemainEmptyAndAreNeverEvicted() {
        SimulationResult result = new LruPageReplacement(5).simulate(Arrays.asList(1, 2, 1));
        assertEquals(2, result.getFaultCount());
        assertEquals(Arrays.asList(1, 2, null, null, null), result.getFinalFrames());
        assertEquals(PageAccess.Outcome.FAULT_LOAD, result.getAccesses().get(1).getOutcome());
    }

    @Test
    void emptyReferenceStringProducesEmptyTraceAndZeroRate() {
        SimulationResult result = new LruPageReplacement(2).simulate(Collections.<Integer>emptyList());
        assertEquals(0, result.getRequestCount());
        assertEquals(0, result.getFaultCount());
        assertEquals(0, result.getHitCount());
        assertEquals(0.0, result.getFaultRate(), 0.0);
        assertEquals(Arrays.asList(null, null), result.getFinalFrames());
    }

    @Test
    void pageNumbersAreNotLimitedToNineteen() {
        SimulationResult result = new LruPageReplacement(2)
                .simulate(Arrays.asList(0, 2_000_000, Integer.MAX_VALUE, 0));
        assertEquals(4, result.getFaultCount());
        assertEquals(Arrays.asList(Integer.MAX_VALUE, 0), result.getFinalFrames());
    }

    @Test
    void differentRunsDoNotShareFrameOrRecencyState() {
        LruPageReplacement simulator = new LruPageReplacement(2);
        SimulationResult first = simulator.simulate(Arrays.asList(1, 2, 3));
        SimulationResult second = simulator.simulate(Arrays.asList(1, 1));
        assertEquals(Arrays.asList(3, 2), first.getFinalFrames());
        assertEquals(Arrays.asList(1, null), second.getFinalFrames());
        assertEquals(1, second.getFaultCount());
        assertEquals(1, second.getHitCount());
    }

    @Test
    void resultAndSnapshotsCannotBeModifiedByCallers() {
        SimulationResult result = new LruPageReplacement(2).simulate(Arrays.asList(1, 2, 3));
        assertThrows(UnsupportedOperationException.class, () -> result.getAccesses().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.getFinalFrames().set(0, 88));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getAccesses().get(0).getFrames().set(0, 88));
        assertEquals(Arrays.asList(3, 2), result.getFinalFrames());
    }

    @Test
    void previousSnapshotsRemainUnchangedAfterLaterReplacements() {
        SimulationResult result = new LruPageReplacement(2).simulate(Arrays.asList(1, 2, 3));
        assertEquals(Arrays.asList(1, null), result.getAccesses().get(0).getFrames());
        assertEquals(Arrays.asList(1, 2), result.getAccesses().get(1).getFrames());
        assertEquals(Arrays.asList(3, 2), result.getAccesses().get(2).getFrames());
    }

    @Test
    void reportsAccurateOutcomesAndAccessDetails() {
        SimulationResult result = new LruPageReplacement(1).simulate(Arrays.asList(4, 4, 5));
        PageAccess load = result.getAccesses().get(0);
        PageAccess hit = result.getAccesses().get(1);
        PageAccess replacement = result.getAccesses().get(2);
        assertEquals(0, load.getRequestIndex());
        assertEquals(4, load.getPageId());
        assertEquals(0, load.getFrameIndex());
        assertTrue(load.isFault());
        assertFalse(load.isHit());
        assertNull(load.getEvictedPage());
        assertTrue(hit.isHit());
        assertFalse(hit.isFault());
        assertEquals(PageAccess.Outcome.FAULT_REPLACE, replacement.getOutcome());
        assertEquals(Integer.valueOf(4), replacement.getEvictedPage());
    }

    @Test
    void invalidFrameCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LruPageReplacement(0));
        assertThrows(IllegalArgumentException.class, () -> new LruPageReplacement(-1));
    }

    @Test
    void invalidPageListsAreRejected() {
        LruPageReplacement simulator = new LruPageReplacement(2);
        assertThrows(NullPointerException.class, () -> simulator.simulate(null));
        assertThrows(NullPointerException.class, () -> simulator.simulate(Arrays.asList(1, null)));
        assertThrows(IllegalArgumentException.class, () -> simulator.simulate(Arrays.asList(1, -1)));
    }

    @Test
    void inputSequenceIsNotMutated() {
        List<Integer> input = new ArrayList<>(Arrays.asList(3, 2, 3, 4));
        new LruPageReplacement(2).simulate(input);
        assertEquals(Arrays.asList(3, 2, 3, 4), input);
    }

    @Test
    void randomizedTracesMatchIndependentAccessOrderedReferenceModel() {
        Random random = new Random(20261122L);
        for (int caseNumber = 0; caseNumber < 1800; caseNumber++) {
            int capacity = random.nextInt(8) + 1;
            int length = random.nextInt(70);
            List<Integer> pages = new ArrayList<>();
            for (int index = 0; index < length; index++) {
                pages.add(random.nextInt(18));
            }
            checkAgainstReference(capacity, pages);
        }
    }

    /** Uses access-ordered LinkedHashMap as an independent eviction oracle. */
    private static void checkAgainstReference(int capacity, List<Integer> pages) {
        SimulationResult actual = new LruPageReplacement(capacity).simulate(pages);
        Map<Integer, Integer> recent = new LinkedHashMap<>(16, 0.75f, true);
        int expectedHits = 0;
        int expectedFaults = 0;
        for (int index = 0; index < pages.size(); index++) {
            int page = pages.get(index);
            Integer frame = recent.get(page); // A successful get updates access order.
            PageAccess.Outcome expectedOutcome;
            Integer victim = null;
            if (frame != null) {
                expectedHits++;
                expectedOutcome = PageAccess.Outcome.HIT;
            } else {
                expectedFaults++;
                if (recent.size() < capacity) {
                    int freeFrame = 0;
                    while (recent.containsValue(freeFrame)) {
                        freeFrame++;
                    }
                    frame = freeFrame;
                    expectedOutcome = PageAccess.Outcome.FAULT_LOAD;
                } else {
                    Iterator<Map.Entry<Integer, Integer>> iterator = recent.entrySet().iterator();
                    Map.Entry<Integer, Integer> oldest = iterator.next();
                    victim = oldest.getKey();
                    frame = oldest.getValue();
                    iterator.remove();
                    expectedOutcome = PageAccess.Outcome.FAULT_REPLACE;
                }
                recent.put(page, frame);
            }
            List<Integer> expectedFrames = new ArrayList<>(Collections.nCopies(capacity, null));
            for (Map.Entry<Integer, Integer> entry : recent.entrySet()) {
                expectedFrames.set(entry.getValue(), entry.getKey());
            }
            PageAccess actualStep = actual.getAccesses().get(index);
            assertEquals(expectedOutcome, actualStep.getOutcome());
            assertEquals(frame.intValue(), actualStep.getFrameIndex());
            assertEquals(victim, actualStep.getEvictedPage());
            assertEquals(expectedFrames, actualStep.getFrames());
        }
        assertEquals(expectedHits, actual.getHitCount());
        assertEquals(expectedFaults, actual.getFaultCount());
    }
}
