package com.example.gqw.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class AppErrorController implements ErrorController {

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request) {
        int statusCode = resolveStatusCode(request);
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            statusCode = status.value();
        }

        Throwable error = resolveThrowable(request);
        String fallbackMessage = resolveErrorMessage(request);
        String requestPath = resolveRequestPath(request);
        String title = AppErrorSupport.titleForStatus(statusCode);
        String message = AppErrorSupport.userMessage(statusCode, error, fallbackMessage);

        if (AppErrorSupport.isAjax(request)) {
            return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(message);
        }

        ModelAndView modelAndView = new ModelAndView(AppErrorSupport.resolveViewName(requestPath));
        modelAndView.setStatus(status);
        modelAndView.addObject("errorStatus", statusCode);
        modelAndView.addObject("errorTitle", title);
        modelAndView.addObject("errorMessage", message);
        modelAndView.addObject("errorPath", requestPath);
        modelAndView.addObject("errorTimestamp", OffsetDateTime.now().toString());
        return modelAndView;
    }

    private static int resolveStatusCode(HttpServletRequest request) {
        Object statusAttr = request != null ? request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) : null;
        if (statusAttr instanceof Integer status) {
            return status;
        }
        if (statusAttr instanceof String statusText) {
            try {
                return Integer.parseInt(statusText);
            } catch (NumberFormatException ignored) {
                return HttpStatus.INTERNAL_SERVER_ERROR.value();
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private static Throwable resolveThrowable(HttpServletRequest request) {
        Object ex = request != null ? request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) : null;
        return ex instanceof Throwable throwable ? throwable : null;
    }

    private static String resolveErrorMessage(HttpServletRequest request) {
        Object msg = request != null ? request.getAttribute(RequestDispatcher.ERROR_MESSAGE) : null;
        return msg instanceof String text ? text : null;
    }

    private static String resolveRequestPath(HttpServletRequest request) {
        Object path = request != null ? request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) : null;
        if (path instanceof String text && !text.isBlank()) {
            return text;
        }
        return request != null ? request.getRequestURI() : "";
    }
}
