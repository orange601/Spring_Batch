package orange.spring.batch.sample;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import javax.persistence.EntityManagerFactory;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.batch.item.file.transform.LineAggregator;
import org.springframework.batch.item.json.JacksonJsonObjectMarshaller;
import org.springframework.batch.item.json.JsonFileItemWriter;
import org.springframework.batch.item.json.builder.JsonFileItemWriterBuilder;
import org.springframework.batch.item.querydsl.reader.QuerydslPagingItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orange.spring.batch.entity.Progress;
import orange.spring.batch.listener.ProgressListener;
import orange.spring.batch.part3.Person;
import orange.spring.batch.support.ProgressRepositorySupport;
import orange.spring.batch.util.ProgressHeaderFooterCallBack;
import orange.spring.batch.util.ProgressJsonItemAggregator;

/**
 * TODO: insert??? chunk??? ???????????? select??? chunk??? ???????????? ?????? ???????????? ????????? ???????????? ???????????? ?????????
 * */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ProgressConfiguration {
	private final JobBuilderFactory jobBuilderFactory;
	private final StepBuilderFactory stepBuilderFactory;
	private final EntityManagerFactory entityManagerFactory;
	//private final ProgressRepository progressRepository;
	private final ProgressRepositorySupport progressRepositorySupport;
	
	// TODO: chunk ???????????? ????????? ??????????????? ????????? ????????? ?????????
	private final int chunkSize = 10;
	
	@Bean
	public Job statisticsJob() throws Exception {
		return jobBuilderFactory.get("progress")
				.incrementer(new RunIdIncrementer())
				.start(this.findAllProgressDataStep())
                //.listener(new ProgressListener.ProgressJobListener())
				.build();
	}
	
	/**
	 * @Note ??????????????? Tasklet?????? ?????? ??????
	 * @Note <INPUT, OUTPUT>chunk(int)
	 * @Note 1.reader?????? input??? ??????
	 * @Note 2. process??? input??? ?????? processing ??? output??? ??????
	 * @Note 3. writer??? List<output>??? ?????? write
	 * */
	@Bean
	@JobScope // @Value("#{jobParameters[key]}" ??? ???????????? ?????? jobscope step scope??? ????????????. >>  ?????? ????????? job??? ??????????????? ??????????????? ????????? ??????????????? ??????????????????.
	public Step findAllProgressDataStep() throws Exception {
		return stepBuilderFactory.get("findAllProgressDataStep")
				.<Progress, Progress>chunk(chunkSize) // /Reader??? ???????????? & Writer??? ??????????????????
                .reader(progressItemReader())
                //.processor(progressItemProcessor())
                .writer(jsonFileItemWriter())
                //.listener(new ProgressListener.ProgressStepListener())
				.build();
	}
	
	// TODO: chunksize?????? ??????(?????????)??????.. ????????? ??????.. ???????????? ????????? (connection ???????????)
    @Bean
    public QuerydslPagingItemReader<Progress> progressItemReader() {
        return new QuerydslPagingItemReader<>(entityManagerFactory, chunkSize, queryFactory -> progressRepositorySupport.findProgress());
    }
    
    private ItemProcessor<? super Progress, ? extends Progress> progressItemProcessor() {
    	 return item -> {
             if (!item.getName().equals("")) {
            	 log.info("ZONE : {}", item);
                 return item;
             }
             return null;
         };
    }
    
    /**
     * @category json?????? ?????????
     * */
    public ItemWriter<Progress> jsonFileItemWriter() throws Exception {
    	ProgressHeaderFooterCallBack progressHeaderFooterCallBack = new ProgressHeaderFooterCallBack();
    	String fileName = this.getFullPath("json");
        FlatFileItemWriter<Progress> itemWriter = new FlatFileItemWriterBuilder<Progress>()
                .resource(new FileSystemResource(fileName))
                .lineAggregator(new ProgressJsonItemAggregator<Progress>())
                .name("statisticsItemWriter")
                .shouldDeleteIfExists(true)
                //.headerCallback(progressHeaderFooterCallBack)
                .footerCallback(write -> write.write("]"))
                .encoding("UTF-8")
                //.lineSeparator(",")
                .build();
        
        itemWriter.afterPropertiesSet();
    	
    	return itemWriter;
    }
    
    /**
     * @category csv?????? ?????????
     * */
    private ItemWriter<Progress> progressItemWriter() throws Exception {
    	LocalDate localDate = LocalDate.now();
    	
    	DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MM");
    	DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd");
    	
    	String month = localDate.format(monthFormatter);
    	String day = localDate.format(dayFormatter);
    	
        String fileName = localDate.getYear() + "-" + month + "-" + day + ".csv";
        
        BeanWrapperFieldExtractor<Progress> fieldExtractor = new BeanWrapperFieldExtractor<>();
        fieldExtractor.setNames(new String[] {"seq", "name"});
        
        DelimitedLineAggregator<Progress> lineAggregator = new DelimitedLineAggregator<>();
        lineAggregator.setDelimiter(",");
        lineAggregator.setFieldExtractor(fieldExtractor);
        
        FlatFileItemWriter<Progress> itemWriter = new FlatFileItemWriterBuilder<Progress>()
                .resource(new FileSystemResource("output/" + fileName))
                .lineAggregator(lineAggregator)
                .name("statisticsItemWriter")
                .encoding("UTF-8")
                .build();
        itemWriter.afterPropertiesSet();
        
    	return itemWriter;

    }
    
    private String getFullPath(String extension) {
    	String path = "Y:" + File.separator + "IDAS" + File.separator + "data"+ File.separator + "pcviewProgress" + File.separator;
    	LocalDate localDate = LocalDate.now();
    	DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MM");
    	DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd");
    	String month = localDate.format(monthFormatter);
    	String day = localDate.format(dayFormatter);
    	return path + localDate.getYear() + "-" + month + "-" + day + "." + extension;
    }
    
}
