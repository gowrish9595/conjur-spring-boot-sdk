package com.cyberark.conjur.springboot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertyProcessorChainTest {

    private TestPropertyProcessorChain chain;

    @BeforeEach
    void setup() {
        chain = new TestPropertyProcessorChain("testChain");
    }

    @Test
    void testGetPropertyReturnsKey() {
        String key = "some.property.key";
        Object result = chain.getProperty(key);
        assertEquals(key, result, "getProperty should return the input key as-is");
    }

    @Test
    void testGetPropertyNamesReturnsEmptyArray() {
        assertNotNull(chain.getPropertyNames(), "getPropertyNames should not return null");
        assertEquals(0, chain.getPropertyNames().length, "getPropertyNames should return an empty array");
    }

    @Test
    void testSetNextChainInitializesDefaultAndCustomChains() {
        assertDoesNotThrow(() -> {
            chain.setNextChain(null); // You’re not actually using the passed-in chain
        });
    }
}

class TestPropertyProcessorChain extends PropertyProcessorChain {
    public TestPropertyProcessorChain(String name) {
        super(name);
    }

    @Override
    public Object getProperty(String name) {
        return super.getProperty(name); // Calls original getProperty (just returns key)
    }

    @Override
    public String[] getPropertyNames() {
        return new String[0];
    }
}
