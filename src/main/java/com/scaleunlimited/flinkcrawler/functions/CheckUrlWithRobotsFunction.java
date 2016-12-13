package com.scaleunlimited.flinkcrawler.functions;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.RichProcessFunction;
import org.apache.flink.util.Collector;

import com.scaleunlimited.flinkcrawler.fetcher.BaseFetcher;
import com.scaleunlimited.flinkcrawler.pojos.FetchStatus;
import com.scaleunlimited.flinkcrawler.pojos.FetchUrl;
import com.scaleunlimited.flinkcrawler.robots.BaseRobotsParser;
import com.scaleunlimited.flinkcrawler.utils.UrlLogger;

@SuppressWarnings("serial")
public class CheckUrlWithRobotsFunction extends RichProcessFunction<FetchUrl, Tuple2<FetchStatus, FetchUrl>> {

	// TODO pick good time for this
	private static final long QUEUE_CHECK_DELAY = 10;

	private BaseFetcher _fetcher;
	private BaseRobotsParser _checker;
	
	// TODO we need a map from domain to rules & refresh time, that we maintain here
	// Actually the key needs to be protocol + full domain (not just PLD) + port, as robots are
	// specific to that combination.
	// TODO we need a map from domain to sitemap & refresh time, maintained here.
	
	private transient ConcurrentLinkedQueue<FetchUrl> _queue;
	
	public CheckUrlWithRobotsFunction(BaseFetcher fetcher, BaseRobotsParser checker) {
		_fetcher = fetcher;
		_checker = checker;
	}
	
	@Override
	public void open(Configuration parameters) throws Exception {
		super.open(parameters);
		
		_queue = new ConcurrentLinkedQueue<>();
	}
	
	@Override
	public void processElement(final FetchUrl url, Context context, Collector<Tuple2<FetchStatus, FetchUrl>> collector) throws Exception {
		UrlLogger.record(this.getClass(), url);
		
		_queue.add(url);
		
		// Every time we get called, we'll set up a new timer that fires
		context.timerService().registerProcessingTimeTimer(context.timerService().currentProcessingTime() + QUEUE_CHECK_DELAY);
	}

	@Override
	public void onTimer(long time, OnTimerContext context, Collector<Tuple2<FetchStatus, FetchUrl>> collector) throws Exception {
		if (!_queue.isEmpty()) {
			FetchUrl url = _queue.remove();
			System.out.println("Url passed robots check: " + url);
			collector.collect(new Tuple2<FetchStatus, FetchUrl>(null, url));
			
			// TODO If we don't pass robots, we should instead collect a (status, null)
		}

		context.timerService().registerProcessingTimeTimer(context.timerService().currentProcessingTime() + QUEUE_CHECK_DELAY);
	}

}
