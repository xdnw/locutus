package com.jpson;

public enum PsonOptions {

    /**
     * Static dictionary only.
     */
    None,

    /**
     * Add keys to the dictionary progressively.
     */
    ProgressiveKeys,

    /**
     * Add values to the dictionary progressively.
     */
    ProgressiveValues,

    /**
     * Add both keys and values to the dictionary progressively.
     */
    Progressive
}