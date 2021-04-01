package com.kiwigrid.k8s.helm.tasks;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

/**
 * @author wind57
 */
class LoggerBodyPublisher implements Flow.Subscriber<ByteBuffer> {

	private final List<String> raw = new ArrayList<>();

	@Override
	public void onSubscribe(Flow.Subscription subscription) {
		subscription.request(Long.MAX_VALUE);
	}

	@Override
	public void onNext(ByteBuffer item) {
		raw.add(new String(item.array()));
	}

	@Override
	public void onError(Throwable throwable) {
		throw new RuntimeException(throwable);
	}

	@Override
	public void onComplete() {
		// NO-OP on purpose because we gather all the pieces into "raw"
		// and then query this method separately
	}

	String raw() {
		return String.join("\n", raw);
	}
}
