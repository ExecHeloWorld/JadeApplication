package com.company;

/**
 * Created by localadmin on 04.12.2016.
 */

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.io.Serializable;

public class Question implements  Serializable
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

    static ArrayList<ArrayList<Question>> Permutation(ArrayList<Question> questionsFromMin, ArrayList<Question> questionsFromMax){
        ArrayList<ArrayList<Question>> result = null;
        ArrayList<Question> kostil = null;
        if(questionsFromMin.get(0).section != questionsFromMax.get(0).section && questionsFromMin.get(1).section != questionsFromMax.get(1).section) {
            result = new ArrayList<>();
            kostil = new ArrayList<>();
            kostil.add(questionsFromMin.get(0));
            kostil.add(questionsFromMax.get(0));
            kostil.add(questionsFromMin.get(1));
            kostil.add(questionsFromMax.get(1));
            result.add(kostil);

        }
        if(questionsFromMin.get(0).section != questionsFromMax.get(1).section && questionsFromMin.get(1).section != questionsFromMax.get(0).section) {
            kostil = new ArrayList<>();
            result = new ArrayList<>();

            kostil.add(questionsFromMin.get(0));
            kostil.add(questionsFromMax.get(1));
            kostil.add(questionsFromMin.get(1));
            kostil.add(questionsFromMax.get(0));
            result.add(kostil);
        }
        return result;
    }

    public static ArrayList<Question>  GetSolutionPermutation(ArrayList<Question> questionsFromMin, ArrayList<Question> questionsFromMax){
        ArrayList<ArrayList<Question>> permutations = Permutation(questionsFromMin,questionsFromMax);
        if(permutations == null) {
            System.out.println("!!!!!!!Permutations null!!!!!");
            return null;
        }

        Integer min = 9999;
        ArrayList<Question> result = null;
        for(ArrayList<Question> listQuestions : permutations){
            Integer sum1 = listQuestions.get(0).complexity + listQuestions.get(1).complexity;
            Integer sum2 = listQuestions.get(2).complexity + listQuestions.get(3).complexity;

            if(Math.abs(sum1 - sum2) < min){
                min = Math.abs(sum1 - sum2);
                result = listQuestions;
            }
        }
        return result;
    }

}

