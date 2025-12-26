package com.hao.withME.utils;

import java.util.*;

/**
 * 算法工具类
 *
 */
public class AlgorithmUtils {

    /**
     * 编辑距离算法（用于计算最相似的两组标签）
     * 原理：https://blog.csdn.net/DBC_121/article/details/104198838
     *
     * @param tagList1
     * @param tagList2
     * @return
     */
    public static int minDistance(List<String> tagList1, List<String> tagList2) {
        int n = tagList1.size();
        int m = tagList2.size();

        if (n * m == 0) {
            return n + m;
        }

        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i < n + 1; i++) {
            d[i][0] = i;
        }

        for (int j = 0; j < m + 1; j++) {
            d[0][j] = j;
        }

        for (int i = 1; i < n + 1; i++) {
            for (int j = 1; j < m + 1; j++) {
                int left = d[i - 1][j] + 1;
                int down = d[i][j - 1] + 1;
                int left_down = d[i - 1][j - 1];
                if (!Objects.equals(tagList1.get(i - 1), tagList2.get(j - 1))) {
                    left_down += 1;
                }
                d[i][j] = Math.min(left, Math.min(down, left_down));
            }
        }
        return d[n][m];
    }


    /**
     * 编辑距离算法（用于计算最相似的两个字符串）
     * 原理：https://blog.csdn.net/DBC_121/article/details/104198838
     *
     * @param word1
     * @param word2
     * @return
     */
    public static int minDistance(String word1, String word2) {
        int n = word1.length();
        int m = word2.length();

        if (n * m == 0) {
            return n + m;
        }

        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i < n + 1; i++) {
            d[i][0] = i;
        }

        for (int j = 0; j < m + 1; j++) {
            d[0][j] = j;
        }

        for (int i = 1; i < n + 1; i++) {
            for (int j = 1; j < m + 1; j++) {
                int left = d[i - 1][j] + 1;
                int down = d[i][j - 1] + 1;
                int left_down = d[i - 1][j - 1];
                if (word1.charAt(i - 1) != word2.charAt(j - 1)) {
                    left_down += 1;
                }
                d[i][j] = Math.min(left, Math.min(down, left_down));
            }
        }
        return d[n][m];
    }

    /**
     * 余弦相似度算法
     * 结果范围：[0, 1]，值越大越相似
     *
     * @param tagList1 用户1的标签列表
     * @param tagList2 用户2的标签列表
     * @return 相似度分数
     */
    public static double cosineSimilarity(List<String> tagList1, List<String> tagList2) {
        if (tagList1 == null || tagList2 == null || tagList1.isEmpty() || tagList2.isEmpty()) {
            return 0.0;
        }
        // 1. 统计标签频率
        Map<String, Integer> map1 = getFrequencyMap(tagList1);
        Map<String, Integer> map2 = getFrequencyMap(tagList2);

        // 2. 取出两个人标签并集
        Set<String> uniqueTags = new HashSet<>();
        uniqueTags.addAll(map1.keySet());
        uniqueTags.addAll(map2.keySet());

        // 初始化
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (String tag : uniqueTags) {
            //匹配个人标签在所有标签中出现的频率（构造个人标签向量）
            int v1 = map1.getOrDefault(tag, 0);
            int v2 = map2.getOrDefault(tag, 0);

            //点积
            dotProduct += v1 * v2;
            norm1 += Math.pow(v1, 2);
            norm2 += Math.pow(v2, 2);
        }

        // 3 如果两个人标签完全不同，直接返回相似度为 0
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        //4 根据余弦相似定理返回相似度
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 辅助方法：统计列表元素频次
     */
    private static Map<String, Integer> getFrequencyMap(List<String> list) {
        Map<String, Integer> map = new HashMap<>();
        for (String s : list) {
            map.put(s, map.getOrDefault(s, 0) + 1);
        }
        return map;
    }
}
