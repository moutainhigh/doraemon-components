package org.doraemon.component.uid.impl;

import org.doraemon.component.uid.UidGenerator;
import org.doraemon.component.uid.worker.BitsAllocator;
import org.doraemon.component.uid.worker.service.WorkerIdAssigner;
import org.doraemon.framework.exception.ApplicationException;
import org.doraemon.framework.exception.BusinessException;
import org.doraemon.framework.util.DateUtils;
import org.doraemon.framework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.TimeUnit;

/**
 * @description: 描述
 * @author: fengwenping
 * @date: 2020-07-05 17:37
 */
@Named("defaultUidGenerator")
public class DefaultUidGenerator implements UidGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUidGenerator.class);

    @Inject
    @Named("disposableWorkerIdAssigner")
    private WorkerIdAssigner workerIdAssigner;


    /**
     * Bits allocate
     */
    protected int timeBits = 28;
    protected int workerBits = 22;
    protected int seqBits = 13;

    /**
     * Customer epoch, unit as second. For example 2016-05-20 (ms: 1463673600000)
     */
    protected String epochStr = "2016-05-20";
    protected long epochSeconds = TimeUnit.MILLISECONDS.toSeconds(1463673600000L);

    /**
     * Stable fields after spring bean initializing
     */
    protected BitsAllocator bitsAllocator;
    protected long workerId;

    /**
     * Volatile fields caused by nextId()
     */
    protected long sequence = 0L;
    protected long lastSecond = -1L;

    @PostConstruct
    public void afterPropertiesSet() throws Exception {
        // initialize bits allocator
        bitsAllocator = new BitsAllocator(timeBits, workerBits, seqBits);

        // initialize worker id
        workerId = workerIdAssigner.assignWorkerId();
        if (workerId > bitsAllocator.getMaxWorkerId()) {
            throw new RuntimeException("Worker id " + workerId + " exceeds the max " + bitsAllocator.getMaxWorkerId());
        }
        LOGGER.info("Initialized bits(1, {}, {}, {}) for workerID:{}", workerBits, workerBits, seqBits, workerId);
    }

    @Override
    public long getUID() throws ApplicationException {
        try {
            return nextId();
        } catch (Exception e) {
            throw new ApplicationException("Generate unique id exception. ", e);
        }
    }

    protected synchronized long nextId() {
        long currentSecond = getCurrentSecond();

        // Clock moved backwards, refuse to generate uid
        if (currentSecond < lastSecond) {
            long refusedSeconds = lastSecond - currentSecond;
            BusinessException.throwNewException(String.format("Clock moved backwards. Refusing for %d seconds", refusedSeconds));
        }

        // At the same second, increase sequence
        if (currentSecond == lastSecond) {
            sequence = (sequence + 1) & bitsAllocator.getMaxSequence();
            // Exceed the max sequence, we wait the next second to generate uid
            if (sequence == 0) {
                currentSecond = getNextSecond(lastSecond);
            }

            // At the different second, sequence restart from zero
        } else {
            sequence = 0L;
        }

        lastSecond = currentSecond;

        // Allocate bits for UID
        return bitsAllocator.allocate(currentSecond - epochSeconds, workerId, sequence);
    }

    /**
     * Get next millisecond
     */
    private long getNextSecond(long lastTimestamp) {
        long timestamp = getCurrentSecond();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentSecond();
        }

        return timestamp;
    }

    /**
     * Get current second
     */
    private long getCurrentSecond() {
        long currentSecond = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        if (currentSecond - epochSeconds > bitsAllocator.getMaxDeltaSeconds()) {
            BusinessException.throwNewException("Timestamp bits is exhausted. Refusing UID generate. Now: " + currentSecond);
        }

        return currentSecond;
    }

    /**
     * Setters for spring property
     */
    public void setWorkerIdAssigner(WorkerIdAssigner workerIdAssigner) {
        this.workerIdAssigner = workerIdAssigner;
    }

    public void setTimeBits(int timeBits) {
        if (timeBits > 0) {
            this.timeBits = timeBits;
        }
    }

    public void setWorkerBits(int workerBits) {
        if (workerBits > 0) {
            this.workerBits = workerBits;
        }
    }

    public void setSeqBits(int seqBits) {
        if (seqBits > 0) {
            this.seqBits = seqBits;
        }
    }

    public void setEpochStr(String epochStr) {
        if (StringUtils.isNotBlank(epochStr)) {
            this.epochStr = epochStr;
            this.epochSeconds = TimeUnit.MILLISECONDS.toSeconds(DateUtils.parseByDayPattern(epochStr).getTime());
        }
    }
}
