package com.tamaumi.springbatch.batchschedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
@EnableScheduling
@EnableBatchProcessing
public class SpringBatchScheduler {
    private static int i = 0;

    private AtomicBoolean enabled = new AtomicBoolean(true);

    private AtomicInteger batchRunCounter = new AtomicInteger(0);

    private final Map<Object, ScheduledFuture<?>> scheduledTasks = new IdentityHashMap<>();

    @Autowired
    private JobBuilderFactory jobBuilderFactory;

    @Autowired
    private StepBuilderFactory stepBuilderFactory;

    @Autowired
    private JobLauncher jobLauncher;

    @Scheduled(fixedRate = 5000)
    public void launchJob() throws Exception {
        Date date = new Date();
        log.debug("scheduler starts at " + date);
        log.debug("-------------------------------------------");
        log.debug("number: "+ (i++));
        log.debug("-------------------------------------------");
        if (enabled.get()) {
            JobExecution jobExecution = jobLauncher
                    .run(
                            job(),
                            new JobParametersBuilder().addDate("launchDate", date).toJobParameters()
                    );
            batchRunCounter.incrementAndGet();
            log.debug("Batch job ends with status as " + jobExecution.getStatus());
        }
        log.debug("scheduler ends ");
    }

    public void stop() {
        enabled.set(false);
    }

    public void start() {
        enabled.set(true);
    }

    @Bean
    public TaskScheduler poolScheduler() {
        return new CustomTaskScheduler();
    }

    private class CustomTaskScheduler extends ThreadPoolTaskScheduler {

        private static final long serialVersionUID = -7142624085505040603L;

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long period) {
            ScheduledFuture<?> future = super.scheduleAtFixedRate(task, period);

            ScheduledMethodRunnable runnable = (ScheduledMethodRunnable) task;
            scheduledTasks.put(runnable.getTarget(), future);

            return future;
        }

    }

    public void cancelFutureSchedulerTasks() {
        scheduledTasks.forEach((k, v) -> {
            if (k instanceof SpringBatchScheduler) {
                v.cancel(false);
            }
        });
    }

    @Bean
    public Job job() {
        return jobBuilderFactory
                .get("job")
                .start(readBooks())
                .build();
    }

    @Bean
    protected Step readBooks() {
        return stepBuilderFactory.get("readBooks")
                .<Book, Book>chunk(2)
                .reader(reader())
                .writer(writer())
                .build();
    }

    @Bean
    public ItemReader<? extends Book> reader() {
        return new FlatFileItemReaderBuilder<Book>().name("bookItemReader")
                .resource(new ClassPathResource("books.csv"))
                .delimited()
                .names(new String[]{"id", "name"})
                .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {
                    {
                        setTargetType(Book.class);
                    }
                })
                .build();
    }

    @Bean
    public ItemWriter<? super Book> writer() {
        return new ItemWriter<Book>() {

            @Override
            public void write(List<? extends Book> items) throws Exception {
                log.debug("writer..." + items.size());
                for (Book item : items) {
                    log.debug(item.toString());
                }

            }
        };
    }

    public AtomicInteger getBatchRunCounter() {
        return batchRunCounter;
    }
}