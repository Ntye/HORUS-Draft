package horus.bootstrap;

import horus.adapter.index.CoLocatedIndexAdapter;
import horus.application.blocking.MeasureBlockingRecall;
import horus.blocking.BlockingIndex;
import horus.blocking.CandidateGenerator;
import horus.blocking.Strategy;
import horus.normalisation.NormalisationPipeline;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Composition root for blocking. I-7: strategy list, cap and trigram threshold are configuration;
// an invalid value fails the boot (CandidateGenerator / CoLocatedIndexAdapter validate on
// construction), never a screening. Env-sourced until ConfigVersion is wired in Step 9.
@Configuration
public class BlockingConfig {

    @Bean
    public BlockingIndex blockingIndex(
            @Qualifier("screenDataSource") DataSource screenDataSource,
            @Value("${HORUS_BLOCKING_MAX_CANDIDATES:500}") int maxCandidatesPerStrategy,
            @Value("${HORUS_BLOCKING_TRIGRAM_THRESHOLD:0.3}") double trigramThreshold) {
        return new CoLocatedIndexAdapter(screenDataSource, maxCandidatesPerStrategy, trigramThreshold);
    }

    @Bean
    public CandidateGenerator candidateGenerator(
            BlockingIndex blockingIndex,
            @Value("${HORUS_BLOCKING_STRATEGIES:EXACT_HASH,TRIGRAM,PHONETIC}") String strategies) {
        List<Strategy> configured = Arrays.stream(strategies.split(","))
                .map(String::strip)
                .map(Strategy::valueOf)
                .toList();
        return new CandidateGenerator(blockingIndex, configured);
    }

    @Bean
    public MeasureBlockingRecall measureBlockingRecall(
            NormalisationPipeline normalisationPipeline, CandidateGenerator candidateGenerator) {
        return new MeasureBlockingRecall(normalisationPipeline, candidateGenerator);
    }
}
