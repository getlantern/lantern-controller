package org.lantern.friending;

import static org.junit.Assert.*;

import org.junit.Test;

public class FriendingTest {
    @Test
    public void testMaxFriendsForDegree() {
        assertEquals(20, Friending.maxFriendsForDegree(0));
        assertEquals(8, Friending.maxFriendsForDegree(1));
        assertEquals(5, Friending.maxFriendsForDegree(2));
        assertEquals(4, Friending.maxFriendsForDegree(3));
        assertEquals(3, Friending.maxFriendsForDegree(4));
        assertEquals(2, Friending.maxFriendsForDegree(5));
        assertEquals(2, Friending.maxFriendsForDegree(6));
        assertEquals(1, Friending.maxFriendsForDegree(7));
        assertEquals(1, Friending.maxFriendsForDegree(8));
        assertEquals(1, Friending.maxFriendsForDegree(9));
        assertEquals(1, Friending.maxFriendsForDegree(10));
        assertEquals(1, Friending.maxFriendsForDegree(11));
        assertEquals(1, Friending.maxFriendsForDegree(12));
        assertEquals(1, Friending.maxFriendsForDegree(13));
        assertEquals(1, Friending.maxFriendsForDegree(14));
        assertEquals(1, Friending.maxFriendsForDegree(15));
        assertEquals(1, Friending.maxFriendsForDegree(16));
        assertEquals(1, Friending.maxFriendsForDegree(17));
        assertEquals(1, Friending.maxFriendsForDegree(18));
        assertEquals(0, Friending.maxFriendsForDegree(19));
    }

}
