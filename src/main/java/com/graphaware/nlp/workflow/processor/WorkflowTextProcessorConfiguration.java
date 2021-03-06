/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.graphaware.nlp.workflow.processor;

import com.graphaware.nlp.workflow.WorkflowBaseConfiguration;
import java.util.Map;

/**
 *
 * @author ale
 */
public class WorkflowTextProcessorConfiguration extends WorkflowBaseConfiguration {

    public static final String TEXT_PROCESSOR = "textProcessor";
    public static final String PIPELINE = "pipeline";
    public static final String CHECK_LANGUAGE = "checkLanguage";

    public WorkflowTextProcessorConfiguration(Map<String, Object> configuration) {
        super(configuration);
    }

    public String getTextProcessor() {
        return (String) getConfiguration().get(TEXT_PROCESSOR);
    }

    public String getPipeline() {
        return (String) getConfiguration().get(PIPELINE);
    }

    public boolean checkLanguage() {
        return (Boolean) getConfiguration().getOrDefault(CHECK_LANGUAGE, Boolean.TRUE);
    }

}
