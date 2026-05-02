package com.trimlink.module.queue.entity;

public enum QueueStatus {
    WAITING,     // in the queue, not yet called
    CALLED,      // barber called this customer
    IN_SERVICE,  // service is currently underway
    COMPLETED,   // service done, exited queue
    CANCELLED,   // customer left / removed
    SKIPPED      // customer didn't respond when called
}
