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
package com.graphaware.nlp.persistence.persisters;

import com.graphaware.nlp.domain.*;
import com.graphaware.nlp.persistence.PersistenceRegistry;
import com.graphaware.nlp.persistence.constants.Labels;
import com.graphaware.nlp.persistence.constants.Properties;
import com.graphaware.nlp.persistence.constants.Relationships;
import com.graphaware.nlp.util.SentenceUtils;
import com.graphaware.nlp.util.TagUtils;
import org.neo4j.graphdb.*;

import java.util.HashMap;
import java.util.Map;

public class SentencePersister extends AbstractPersister implements Persister<Sentence> {

    public SentencePersister(GraphDatabaseService database, PersistenceRegistry registry) {
        super(database, registry);
    }

    @Override
    public Node persist(Sentence sentence, String id, String txId) {
        Node sentenceNode = get(sentence, id);
        Node newSentenceNode;
        if (sentenceNode == null) {
            newSentenceNode = getOrCreate(sentence, id, txId);
        } else {
            newSentenceNode = sentenceNode;
        }
        update(newSentenceNode, sentence, id);
        storeSentenceTags(sentence, newSentenceNode, id, txId);
        storeSentenceTagOccurrences(sentence, newSentenceNode, txId);
        storeUniversalDependenciesForSentence(sentence, newSentenceNode);
        storePhrases(sentence, newSentenceNode, txId);
        storeCoreferences(sentence);
        storeCorefOptimized(sentence, id);
        assignSentimentLabel(sentence, newSentenceNode);
        sentenceNode = newSentenceNode;

        return sentenceNode;
    }

    @Override
    public Sentence fromNode(Node node, Object... properties) {
        Map<String, Object> nodeProperties = node.getAllProperties();
        String sentence = nodeProperties.get(configuration().getPropertyKeyFor(Properties.TEXT)).toString();
        int sentenceNumber = (int) nodeProperties.get(configuration().getPropertyKeyFor(Properties.SENTENCE_NUMBER));

        final Sentence sentenceO = new Sentence(sentence, sentenceNumber);

        node.getLabels().forEach(label -> {
            if (Labels.contains(label.name()) &&Labels.isSentimentLabel(label.name())) {
                sentenceO.setSentiment(SentenceUtils.getSentimentLevelForLabel(label));
            }
        });

        return sentenceO;

    }

    @Override
    public boolean exists(String id) {
        return false;
    }

    @Override
    public Node getOrCreate(Sentence sentence, String id, String txId) {
        Node node = database.createNode(configuration().getLabelFor(Labels.Sentence));
        update(node, sentence, id);

        return node;
    }

    @Override
    public void update(Node node, Sentence sentence, String id) {
        node.setProperty(configuration().getPropertyKeyFor(Properties.PROPERTY_ID), String.format("%s_%s", id, sentence.getSentenceNumber()));
        node.setProperty(configuration().getPropertyKeyFor(Properties.SENTENCE_NUMBER), sentence.getSentenceNumber());
        node.setProperty(configuration().getPropertyKeyFor(Properties.HASH), sentence.hash());
        node.setProperty(configuration().getPropertyKeyFor(Properties.TEXT), sentence.getSentence());
    }

    private void storeSentenceTags(Sentence sentence, Node sentenceNode, String id, String txId) {
        sentence.getTags().values().forEach(tag -> {
            Node tagNode = getPersister(Tag.class).getOrCreate(tag, id, txId);
            relateSentenceToTag(sentenceNode, tagNode, tag.getMultiplicity());
        });
    }

    private void relateSentenceToTag(Node sentenceNode, Node tagNode, int multiplicity) {
        Relationship rel = sentenceNode.createRelationshipTo(tagNode, configuration().getRelationshipFor(Relationships.HAS_TAG));
        rel.setProperty(configuration().getPropertyKeyFor(Properties.TF), multiplicity);
    }

    private void storePhrases(Sentence sentence, Node sentenceNode, String txId) {
        sentence.getPhraseOccurrences().values().forEach(phraseOccurrenceAtPosition -> {
            phraseOccurrenceAtPosition.values().forEach(occurrence -> {
                Node phraseNode = getOrCreatePhrase(occurrence.getElement(), txId);
                relateSentenceToPhrase(sentenceNode, phraseNode);
                Node phraseOccurrenceNode = createPhraseOccurrence(occurrence);
                relateSentenceToPhraseOccurrence(sentenceNode, phraseOccurrenceNode);
                relatePhraseOccurrenceToPhrase(phraseOccurrenceNode, phraseNode);
            });
        });
    }

    private void storeSentenceTagOccurrences(Sentence sentence, Node sentenceNode, String txId) {
        sentence.getTagOccurrences().values().forEach(occurrence -> {
            for (TagOccurrence tagAtPosition : occurrence) {
                Node tagNode = getPersister(Tag.class).getOrCreate(tagAtPosition.getElement(), null, txId);
                Node tagOccurrenceNode = createTagOccurrenceNode(tagAtPosition);
                relateTagOccurrenceToTag(tagOccurrenceNode, tagNode);
                relateSentenceToTagOccurrence(sentenceNode, tagOccurrenceNode);
            }
        });
    }

    private void relateSentenceToTagOccurrence(Node sentenceNode, Node tagOccurrenceNode) {
        sentenceNode.createRelationshipTo(tagOccurrenceNode, configuration().getRelationshipFor(Relationships.SENTENCE_TAG_OCCURRENCE));
    }

    private Node createTagOccurrenceNode(TagOccurrence occurrence) {
        Node node = database.createNode(configuration().getLabelFor(Labels.TagOccurrence));
        node.setProperty(configuration().getPropertyKeyFor(Properties.OCCURRENCE_BEGIN), occurrence.getSpan().first());
        node.setProperty(configuration().getPropertyKeyFor(Properties.OCCURRENCE_END), occurrence.getSpan().second());
        node.setProperty(configuration().getPropertyKeyFor(Properties.PART_OF_SPEECH), occurrence.getElement().getPosAsArray());
        node.setProperty(configuration().getPropertyKeyFor(Properties.NAMED_ENTITY), occurrence.getElement().getNeAsArray());
        node.setProperty(configuration().getPropertyKeyFor(Properties.TAG_ORIGINAL_VALUE), occurrence.getValue());
        if (occurrence.hasNamedEntity()) {
            String ne = occurrence.getElement().getNe().get(0);
            String labelName = configuration().getPropertyKeyFor(Properties.NAMED_ENTITY_PREFIX_NEW) + TagUtils.getNamedEntityValue(ne);
            node.addLabel(Label.label(labelName));
            node.setProperty(Properties.CONFIDENCE, occurrence.getConfidence());
        }
        return node;
    }

    private void relateTagOccurrenceToTag(Node tagOccurrence, Node tag) {
        tagOccurrence.createRelationshipTo(tag, configuration().getRelationshipFor(Relationships.TAG_OCCURRENCE_TAG));
    }

    private void storeUniversalDependenciesForSentence(Sentence sentence, Node sentenceNode) {
        final Map<String, Long> tokenIdsToNodeIds = new HashMap<>();
        sentence.getTagOccurrences().values().forEach(occurence -> {
            occurence.forEach(tagOccurrence -> {
                Node tagOccurrenceNode = getTagOccurrenceInSentence(sentenceNode, tagOccurrence);
                if (tagOccurrenceNode == null) {
                    throw new RuntimeException("Expected to find a TagOccurrence node, got null");
                }
                tagOccurrence.getPartIds().forEach(tokenId -> {
                    tokenIdsToNodeIds.put(tokenId, tagOccurrenceNode.getId());
                });
            });
        });

        sentence.getTypedDependencies().forEach(typedDependency -> {
            if (!tokenIdsToNodeIds.containsKey(typedDependency.getSource()) || !tokenIdsToNodeIds.containsKey(typedDependency.getTarget())) {
//                LOG.info("source: {} or target: {} for typed dependency not found", typedDependency.getSource(), typedDependency.getTarget());
                return;
            }

            Node sourceNode = database.getNodeById(tokenIdsToNodeIds.get(typedDependency.getSource()));
            Node targetNode = database.getNodeById(tokenIdsToNodeIds.get(typedDependency.getTarget()));
            relateTypedDependencySourceAndTarget(sourceNode, targetNode, typedDependency);
        });
    }

    private void relateTypedDependencySourceAndTarget(Node source, Node target, TypedDependency typedDependency) {
        RelationshipType relationshipType = RelationshipType.withName(typedDependency.getName().toUpperCase());
        Relationship relationship = source.createRelationshipTo(target, relationshipType);
        if (null != typedDependency.getSpecific()) {
            relationship.setProperty(configuration().getPropertyKeyFor(Properties.DEPENDENCY_SPECIFIC), typedDependency.getSpecific());
        }
        if (relationshipType.name().equals("ROOT")) {
            source.addLabel(configuration().getLabelFor(Labels.Root));
        }
    }

    private void storeCorefOptimized(Sentence sentence, String id) {
        sentence.getTagOccurrences().values().forEach(tagOccurrences -> {
            tagOccurrences.forEach(tagOccurrence -> {
                if (tagOccurrence.hasReference()) {
                    Node sentenceNode = get(sentence, id);
                    Node occurrenceFrom = getTagOccurrenceInSentence(sentenceNode, tagOccurrence);
                    Node occurrenceTo = getTagOccurrenceInSentence(get(tagOccurrence.getCoreference().getSentence(), id), tagOccurrence.getCoreference().getTagOccurrence());
                    if (occurrenceFrom != null && occurrenceTo != null) {
                        boolean shouldCreate = true;
                        for (Relationship relationship : occurrenceFrom.getRelationships(RelationshipType.withName("COREF"), Direction.OUTGOING)) {
                            if (relationship.getEndNode().getId() == occurrenceTo.getId()) {
                                shouldCreate = false;
                            }
                        }
                        if (shouldCreate) {
                            occurrenceFrom.createRelationshipTo(occurrenceTo, RelationshipType.withName("COREF"));
                        }
                    }
                }
            });
        });
    }

    private void assignSentimentLabel(Sentence sentence, Node sentenceNode) {
        int sentiment = sentence.getSentiment();
        Label sentimentLabel = SentenceUtils.getDefaultLabelForSentimentLevel(sentiment);
        if (sentimentLabel == null) {
            return;
        }
        sentenceNode.addLabel(configuration().getLabelFor(sentimentLabel));
    }

    private Node getTagOccurrenceInSentence(Node sentenceNode, TagOccurrence tagOccurrence) {
        for (Relationship relationship : sentenceNode.getRelationships(configuration().getRelationshipFor(Relationships.SENTENCE_TAG_OCCURRENCE), Direction.OUTGOING)) {
            Node otherNode = relationship.getEndNode();
            if (otherNode.getProperty(configuration().getPropertyKeyFor(Properties.OCCURRENCE_BEGIN)).equals(tagOccurrence.getSpan().first())
                    && otherNode.getProperty(configuration().getPropertyKeyFor(Properties.OCCURRENCE_END)).equals(tagOccurrence.getSpan().second())) {
                return otherNode;
            }
        }

        return null;
    }

    private void relateSentenceToPhrase(Node sentenceNode, Node phraseNode) {
        sentenceNode.createRelationshipTo(phraseNode,
                configuration().getRelationshipFor(Relationships.HAS_PHRASE));
    }

    private void relatePhraseOccurrenceToPhrase(Node phraseOccurrenceNode, Node phraseNode) {
        phraseOccurrenceNode.createRelationshipTo(phraseNode,
                configuration().getRelationshipFor(Relationships.PHRASE_OCCURRENCE_PHRASE));
    }

    private void relateSentenceToPhraseOccurrence(Node sentenceNode, Node phraseOccurrenceNode) {
        sentenceNode.createRelationshipTo(phraseOccurrenceNode,
                configuration().getRelationshipFor(Relationships.SENTENCE_PHRASE_OCCURRENCE));
    }

    private Node createPhraseOccurrence(PartOfTextOccurrence<Phrase> occurrence) {
        Node node = database.createNode(configuration().getLabelFor(Labels.PhraseOccurrence));
        node.setProperty(configuration().getPropertyKeyFor(Properties.START_POSITION), occurrence.getSpan().first());
        node.setProperty(configuration().getPropertyKeyFor(Properties.END_POSITION), occurrence.getSpan().second());

        return node;
    }

    private Node getOrCreatePhrase(Phrase phrase, String txId) {
        Node node = getPhraseNode(phrase);

        if (node == null) {
            node = database.createNode(configuration().getLabelFor(Labels.Phrase));
            updatePhrase(phrase, node);
        } else {
            updatePhrase(phrase, node);
        }

        return node;
    }

    private Node getPhraseNode(Phrase phrase) {
       return database.findNode(configuration().getLabelFor(Labels.Phrase),
                configuration().getPropertyKeyFor(Properties.CONTENT_VALUE),
                phrase.getContent()
        );
    }

    private void storeCoreferences(Sentence sentence) {
        sentence.getPhraseOccurrences().values().forEach(phraseOccurrenceAtPosition -> {
            phraseOccurrenceAtPosition.values().forEach(occurrence -> {
                Phrase phrase = occurrence.getElement();
                Phrase reference = phrase.getReference();
                if (reference != null) {
                    Node phraseNode = getPhraseNode(phrase);
                    Node referenceNode = getPhraseNode(reference);
                    if (phraseNode != null && referenceNode != null) {
                        if (!relationshipExistBetween(phraseNode, referenceNode, RelationshipType.withName("COREFERENCE"))) {
                            phraseNode.createRelationshipTo(referenceNode, RelationshipType.withName("COREFERENCE"));
                        }
                    }
                }
            });
        });
    }

    private void updatePhrase(Phrase phrase, Node phraseNode) {
        phraseNode.setProperty(configuration().getPropertyKeyFor(Properties.CONTENT_VALUE), phrase.getContent());
        String type = phrase.getType() != null ? phrase.getType() : NLPDefaultValues.PHRASE_TYPE;
        phraseNode.setProperty(configuration().getPropertyKeyFor(Properties.PHRASE_TYPE), type);
    }

    @Override
    public Node persist(Sentence object) {
        throw new UnsupportedOperationException("This cannot implemented for this persister"); //To change body of generated methods, choose Tools | Templates.
    }

    private Node get(Sentence sentence, String id) {
        String sentenceId = String.format("%s_%s", id, sentence.getSentenceNumber());
        Node sentenceNode = getIfExist(configuration().getLabelFor(Labels.Sentence), configuration().getPropertyKeyFor(Properties.PROPERTY_ID), sentenceId);

        return sentenceNode;
    }
}
