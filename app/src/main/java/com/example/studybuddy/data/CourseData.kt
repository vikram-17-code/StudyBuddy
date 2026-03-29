package com.example.studybuddy.data

import com.example.studybuddy.model.CoursePlan
import com.example.studybuddy.model.TopicDetail

object CourseData {

    val availableCoursePlans = listOf(
        CoursePlan("daa_plan", "DAA", "https://www.geeksforgeeks.org/design-and-analysis-of-algorithms/"),
        CoursePlan("java_plan", "Java", "https://www.oracle.com/java/technologies/"),
        CoursePlan("dsa_plan", "DSA", "https://www.geeksforgeeks.org/data-structures/"),
        CoursePlan("web_plan", "Web Technology", "https://www.w3schools.com/"),
        CoursePlan("se_plan", "Software Engineering", "https://www.tutorialspoint.com/software_engineering/index.htm"),
        CoursePlan("eng_plan", "English", "https://www.britishcouncil.org/"),
        // New Courses Added
        CoursePlan("python_plan", "Python", "https://www.python.org/"),
        CoursePlan("os_plan", "Operating Systems", "https://www.tutorialspoint.com/operating_system/index.htm"),
        CoursePlan("oss_plan", "Open Source Software", "https://opensource.guide/"),
        CoursePlan("ai_plan", "Artificial Intelligence", "https://www.ibm.com/topics/artificial-intelligence"),
        CoursePlan("animation_plan", "Animation", "https://www.animaker.com/blog/what-is-animation/")
    )

    val availableTopics = listOf(
        // DAA
        TopicDetail("daa_t1", "daa_plan", "Asymptotic Analysis", 2, "https://www.geeksforgeeks.org/analysis-of-algorithms-set-1-asymptotic-analysis/", 1),
        TopicDetail("daa_t2", "daa_plan", "Divide and Conquer", 3, "https://www.tutorialspoint.com/data_structures_algorithms/divide_and_conquer.htm", 2),
        // Java
        TopicDetail("java_t1", "java_plan", "OOP Concepts", 1, "https://docs.oracle.com/javase/tutorial/java/concepts/", 1),
        TopicDetail("java_t2", "java_plan", "Collections", 2, "https://www.javatpoint.com/collections-in-java", 2),
        // DSA
        TopicDetail("dsa_t1", "dsa_plan", "Arrays & Linked Lists", 3, "https://www.programiz.com/dsa/linked-list", 1),
        TopicDetail("dsa_t2", "dsa_plan", "Stacks & Queues", 2, "https://www.geeksforgeeks.org/stack-data-structure/", 2),
        // Web
        TopicDetail("web_t1", "web_plan", "HTML5 & CSS3", 2, "https://developer.mozilla.org/en-US/docs/Learn/Getting_started_with_the_web/HTML_basics", 1),
        TopicDetail("web_t2", "web_plan", "JavaScript Basics", 3, "https://javascript.info/", 2),
        // SE
        TopicDetail("se_t1", "se_plan", "SDLC Models", 2, "https://www.tutorialspoint.com/software_engineering/software_engineering_sdlc_models.htm", 1),
        TopicDetail("se_t2", "se_plan", "Agile Methodology", 2, "https://www.atlassian.com/agile", 2),
        // English
        TopicDetail("eng_t1", "eng_plan", "Grammar & Tenses", 2, "https://www.grammarly.com/blog/verb-tenses/", 1),
        TopicDetail("eng_t2", "eng_plan", "Business Communication", 3, "https://www.coursera.org/articles/business-communication", 2),

        // Python
        TopicDetail("python_t1", "python_plan", "Basics & Data Types", 2, "https://docs.python.org/3/tutorial/introduction.html", 1),
        TopicDetail("python_t2", "python_plan", "Functions & Modules", 3, "https://docs.python.org/3/tutorial/controlflow.html", 2),

        // Operating Systems
        TopicDetail("os_t1", "os_plan", "Process Management", 3, "https://www.tutorialspoint.com/operating_system/os_process_scheduling.htm", 1),
        TopicDetail("os_t2", "os_plan", "Memory Management", 2, "https://www.tutorialspoint.com/operating_system/os_memory_management.htm", 2),

        // Open Source Software
        TopicDetail("oss_t1", "oss_plan", "Git & GitHub", 2, "https://git-scm.com/doc", 1),
        TopicDetail("oss_t2", "oss_plan", "Contributing to OSS", 2, "https://opensource.guide/how-to-contribute/", 2),

        // Artificial Intelligence
        TopicDetail("ai_t1", "ai_plan", "Intro to Machine Learning", 3, "https://www.coursera.org/articles/what-is-machine-learning", 1),
        TopicDetail("ai_t2", "ai_plan", "Neural Networks Basic", 3, "https://www.ibm.com/topics/neural-networks", 2),

        // Animation
        TopicDetail("animation_t1", "animation_plan", "Principles of Animation", 2, "https://www.animaker.com/blog/12-principles-of-animation/", 1),
        TopicDetail("animation_t2", "animation_plan", "Keyframing", 3, "https://en.wikipedia.org/wiki/Key_frame", 2)
    )
}
