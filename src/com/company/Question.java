package com.company;

/**
 * Created by localadmin on 04.12.2016.
 */

import java.util.Collections;
import java.util.HashSet;

public class Question
{
    private String name;
    private int complexity;
    private int section;

    public Question(String name, int complexity, int section)
    {
        this.name = name;
        this.complexity = complexity;
        this.section=section;
    }
    public String Name()
    {
        return name;
    }

    public int Complexity()
    {
        return complexity;
    }

    public int Section() {
        return section;
    }
}

