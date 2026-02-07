package com.example.demo;

import java.util.Set;

public interface IKeywordIdentifier
{
    String detectTask(String s);
    public String normalizeIntent(String raw);
    public String detectDomain(String s);
    public String detectOutputFormat(String s);
    public Set<String> contentWords(String s);
    public double jaccard(Set<String> a, Set<String> b);
    public Set<String> topKeywords(String s, int max);

//
}
