/*
 * Copyright (c) 2013-2018 GraphAware
 *
 * This file is part of the GraphAware Framework.
 *
 * GraphAware Framework is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */
package com.graphaware.nlp.dsl.procedure;

import com.graphaware.nlp.dsl.AbstractDSL;
import com.graphaware.nlp.dsl.request.TextRankPostprocessRequest;
import com.graphaware.nlp.dsl.request.TextRankRequest;
import com.graphaware.nlp.dsl.result.SingleResult;
import com.graphaware.nlp.ml.textrank.TextRankProcessor;
import com.graphaware.nlp.ml.textrank.TextRankResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;
import org.neo4j.logging.Log;
import com.graphaware.common.log.LoggerFactory;

public class TextRankProcedure extends AbstractDSL {

    private static final Log LOG = LoggerFactory.getLogger(TextRankProcedure.class);

    @Procedure(name = "ga.nlp.ml.textRank", mode = Mode.WRITE)
    @Description("Keywords Extraction using TextRank algorithm (includes storage of result)")
    public Stream<SingleResult> computeAndStoreTextRank(@Name("textRankRequest") Map<String, Object> textRankRequest) {
        try {
            TextRankRequest request = TextRankRequest.fromMap(textRankRequest);
            TextRankProcessor processor = (TextRankProcessor) getNLPManager().getExtension(TextRankProcessor.class);
            return Stream.of(processor.computeAndStore(request));
        } catch (Exception e) {
            LOG.error("ERROR in TextRank", e);
            throw new RuntimeException(e);
        }
    }

    @Procedure(name = "ga.nlp.ml.textRank.compute", mode = Mode.WRITE)
    @Description("Keywords Extraction using TextRank algorithm ( without storage )")
    public Stream<KeywordResult> computeTextRank(@Name("textRankRequest") Map<String, Object> textRankRequest) {
        try {
            TextRankRequest request = TextRankRequest.fromMap(textRankRequest);
            TextRankProcessor processor = (TextRankProcessor) getNLPManager().getExtension(TextRankProcessor.class);
            TextRankResult result = processor.compute(request);

            return result.getResult().values().stream()
                    .map(keyword -> {
                        return new KeywordResult(keyword.getKeyword(), keyword.getRelevance());
                    });
        } catch (Exception e) {
            LOG.error("Error during TextRank computation", e);
            throw new RuntimeException(e);
        }
    }

    @Procedure(name = "ga.nlp.ml.textRank.postprocess", mode = Mode.WRITE)
    @Description("TextRank post-processing procedure")
    public Stream<SingleResult> textRankPostprocess(@Name("textRankRequest") Map<String, Object> textRankRequest) {
        try {
            TextRankPostprocessRequest request = TextRankPostprocessRequest.fromMap(textRankRequest);
            TextRankProcessor processor = (TextRankProcessor) getNLPManager().getExtension(TextRankProcessor.class);
            return Stream.of(processor.postprocess(request));
        } catch (Exception e) {
            LOG.error("ERROR in TextRank", e);
            throw new RuntimeException(e);
        }
    }


    public class KeywordResult {
        public String value;

        public double relevance;

        public KeywordResult(String value, double relevance) {
            this.value = value;
            this.relevance = relevance;
        }
    }
}
