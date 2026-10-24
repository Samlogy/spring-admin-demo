package com.example.monolith.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Demo scheduled task (shows up in /actuator/scheduledtasks). */
@Component
public class HeartbeatJob {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatJob.class);

    @Scheduled(fixedDelayString = "PT1M")
    public void beat() {
        log.debug("heartbeat");
    }
}
