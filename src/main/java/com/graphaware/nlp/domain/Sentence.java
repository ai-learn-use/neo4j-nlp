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
package com.graphaware.nlp.domain;

import com.fasterxml.jackson.annotation.*;
import com.graphaware.nlp.util.HashFunctions;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Sentence implements Comparable<Sentence> {
    
    public static final int NO_SENTIMENT = -1;
    private Map<String, Tag> tags = new HashMap<>();

    private Map<Integer, List<TagOccurrence>> tagOccurrences = new HashMap<>();
    private Map<Integer, Map<Integer, PartOfTextOccurrence<Phrase>>> phraseOccurrences = new HashMap<>();
    private List<TypedDependency> typedDependencies = new ArrayList<>();
    private Collection<Tag> tagValues;

    private String sentence;
    private int sentiment = NO_SENTIMENT;
    private String id;
    private int sentenceNumber;

    public Sentence() {
    }

    public Sentence(String sentence, int sentenceNumber) {
        this(sentence);
        this.sentenceNumber = sentenceNumber;
    }

    public Sentence(String sentence) {
        this.sentence = sentence;
    }

    public Map<String, Tag> getTags() {
        return tags;
    }

    public Tag addTag(Tag tag) {
        if (tags.containsKey(tag.getLemma())) {
            Tag result = tags.get(tag.getLemma());
            result.incMultiplicity();
            return result;
        } else {
            tags.put(tag.getLemma(), tag);
            return tag;
        }
    }

    public Tag getTag(String k) {
        return tags.get(k);
    }

    public int getSentiment() {
        return sentiment;
    }

    public void setSentiment(int sentiment) {
        this.sentiment = sentiment;
    }

    public String getId() {
        return id;
    }

    public int getSentenceNumber() {
        return sentenceNumber;
    }

    public void addTagOccurrence(int begin, int end, String value, Tag tag) {
        if (begin < 0) {
            throw new RuntimeException("Begin cannot be negative (for tag: " + tag.getLemma() + ")");
        }
        if (tagOccurrences.containsKey(begin))
          tagOccurrences.get(begin).add(new TagOccurrence(tag, begin, end, value));
        else
          tagOccurrences.put(begin, new ArrayList<>(Arrays.asList(new TagOccurrence(tag, begin, end, value))));
    }

    public void addTagOccurrence(int begin, int end, String value, Tag tag, List<String> tokenIds) {
        if (begin < 0) {
            throw new RuntimeException("Begin cannot be negative (for tag: " + tag.getLemma() + ")");
        }
        if (tagOccurrences.containsKey(begin))
            tagOccurrences.get(begin).add(new TagOccurrence(tag, begin, end, value, tokenIds));
        else
            tagOccurrences.put(begin, 
                    new ArrayList<>(Arrays.asList(new TagOccurrence(tag, begin, end, value, tokenIds))));
    }

    public void addTypedDependency(TypedDependency typedDependency) {
        this.typedDependencies.add(typedDependency);
    }

    public List<TypedDependency> getTypedDependencies() {
        return typedDependencies;
    }

    public PartOfTextOccurrence<Tag> getTagOccurrenceByTagValue(String value) {
        for (Integer i : tagOccurrences.keySet()) {
            for (PartOfTextOccurrence<Tag> occurrence : tagOccurrences.get(i)) {
                if (occurrence.getElement().getLemma().equals(value)) {
                    return occurrence;
                }
            }
        }

        return null;
    }

    public Map<Integer, List<TagOccurrence>> getTagOccurrences() {
        return tagOccurrences;
    }

    public void addPhraseOccurrence(int begin, int end, Phrase phrase) {
        if (begin < 0) {
            throw new RuntimeException("Begin cannot be negative (for phrase: " + phrase.getContent() + ")");
        }
        if (phraseOccurrences == null) {
            phraseOccurrences = new HashMap<>();
        }
        if (!phraseOccurrences.containsKey(begin)) {
            phraseOccurrences.put(begin, new HashMap<>());
        }
        //Will update end if already exist
        phraseOccurrences.get(begin).put(end, new PartOfTextOccurrence<>(phrase, begin, end));
    }

    public Phrase getPhraseOccurrence(int begin, int end) {
        if (begin < 0) {
            throw new RuntimeException("Begin cannot be negative");
        }
        Map<Integer, PartOfTextOccurrence<Phrase>> occurrences = phraseOccurrences.get(begin);

        if (occurrences != null && occurrences.containsKey(end)) {
            return occurrences.get(end).getElement();
        }
        return null;
    }

    public Map<Integer, Map<Integer, PartOfTextOccurrence<Phrase>>> getPhraseOccurrences() {
        return phraseOccurrences;
    }


    public String getSentence() {
        return sentence;
    }

    @Override
    public int compareTo(Sentence o) {
        if (o == null || !(o instanceof Sentence))
            return 1;
        return this.sentenceNumber - o.sentenceNumber;
    }

    public Tag getTagOccurrence(int begin) {
        if (begin < 0) {
            throw new RuntimeException("Begin cannot be negative");
        }
        List<TagOccurrence> occurrence = tagOccurrences.get(begin);
        if (occurrence != null) {
            return occurrence.get(0).getElement(); // TO DO: take into account that more than one PartOfTextOccurrence is possible
        } else {
            return null;
        }
    }


    public List<Phrase> getPhraseOccurrence(int begin) {
        if (begin < 0) {
            throw new RuntimeException("Begin cannot be negative");
        }
        Map<Integer, PartOfTextOccurrence<Phrase>> occurrence = phraseOccurrences.get(begin);

        if (occurrence != null) {
            List<Phrase> result = new ArrayList<>();
            occurrence.values().stream().forEach((item) -> {
                result.add(item.getElement());
            });
            return result;
        } else {
            return new ArrayList<>();
        }
    }

    public String hash() {
        return HashFunctions.MD5(sentence);
    }

    public TagOccurrence getTagOccurrenceByValueAndNE(String value, String ne) {
        AtomicReference<TagOccurrence> ref = new AtomicReference<>();
        tagOccurrences.values().forEach(tos -> {
            tos.forEach(to -> {
                if (to.getElement().getOriginalValue().equals(value)) {
                    if (to.getElement().hasNamedEntity() && to.getElement().getNe().get(0).equals(ne)) {
                        ref.set(to);
                    }
                }
            });
        });

        return ref.get();
    }

    public TagOccurrence getTagOccurrenceByValueWithNE(String value) {
        AtomicReference<TagOccurrence> ref = new AtomicReference<>();
        tagOccurrences.values().forEach(tos -> {
            tos.forEach(to -> {
                if (to.getValue().equals(value)) {
                    if (to.hasNamedEntity()) {
                        ref.set(to);
                    }
                }
            });
        });

        return ref.get();
    }
}
