package sg.example.helloauth.logging;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * Carries the handing-over thread's MDC, with its correlation and trace IDs, into background
 * work, so its log lines can still be followed back to the request. Boot applies it to its
 * application task executor.
 */
@Component
class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> handedOver = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> own = MDC.getCopyOfContextMap();
            setContext(handedOver);
            try {
                task.run();
            } finally {
                setContext(own);
            }
        };
    }

    private static void setContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
