package org.styli.services.order.model.redis;

import lombok.Getter;

import java.util.concurrent.TimeUnit;

/**
 * Created on 19-May-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Getter
public class TtlMode {

    public static final TtlMode OTP_REDIS = new TtlMode(24L, TimeUnit.HOURS);
    public static final TtlMode OTP_VALID = new TtlMode(10L, TimeUnit.MINUTES);

    private long value;
    private TimeUnit timeUnit;

    private TtlMode() {
        this(0, TimeUnit.SECONDS);
    }

    private TtlMode( long value, TimeUnit timeUnit) {
        this.value = value;
        this.timeUnit = timeUnit;
    }
}
