package com.codegym.aiplanning.service.daily.step;

import com.codegym.aiplanning.entity.daily.DailyPlanTaskStep;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TaskStepOrderNormalizer {

    public void normalize(List<DailyPlanTaskStep> orderedSteps) {
        for (int index = 0; index < orderedSteps.size(); index++) {
            orderedSteps.get(index).updateOrderIndex(index);
        }
    }
}

