package com.fishmoun.soulroute.agent;

record ReActStepResult(
        String thought,
        String action,
        String actionInput,
        String observation,
        String finalAnswer,
        boolean finished
) {

    static ReActStepResult observed(String thought, String action, String actionInput, String observation) {
        return new ReActStepResult(thought, action, actionInput, observation, null, false);
    }

    static ReActStepResult finished(String thought, String finalAnswer) {
        return new ReActStepResult(thought, "finish", null, null, finalAnswer, true);
    }
}
