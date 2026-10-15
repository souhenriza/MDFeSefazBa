package com.souhenriza.sefazba.domain.port.browser;

import com.souhenriza.sefazba.domain.exceptions.ForbiddenRequestException;
import com.souhenriza.sefazba.domain.exceptions.SefazAuthenticationException;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class NetworkChangesResolver {

       public void resolveAction(Page page, Runnable action, int maxRetries, long timeout) {
        for (int i = 1; i <= maxRetries; i++) {
            try {
                action.run();
                return;
            } catch (ForbiddenRequestException | SefazAuthenticationException e) {
                throw e;
            }  catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : "";
                boolean isNetworkError = message.contains("ERR_NETWORK_CHANGED")
                        || message.contains("ERR_CONNECTION_RESET")
                        || message.contains("ERR_CONNECTION_REFUSED")
                        || message.contains("ERR_CONNECTION_TIMED_OUT")
                        || message.contains("ERR_NAME_NOT_RESOLVED")
                        || message.contains("ERR_SSL")
                        || message.contains("ERR_ABORTED");

                if (!isNetworkError) {
                    throw e;
                }

                log.warn("Network error on action attempt {}/{}: {}", i, maxRetries, message);
            }

            if (i < maxRetries) {
                page.waitForTimeout(timeout);
            }
        }
        throw new SefazInvalidState("Maybe SEFAZ site is unreachable. Try again.");
    }

    public void resolve(Page page, String url, int maxRetries, long timeout) {

        Page.NavigateOptions navigateOptions = new Page.NavigateOptions();
        navigateOptions.setTimeout(60000);

           for (int i = 1; i <= maxRetries ; i++) {
            try {
                Response response = page.navigate(url, navigateOptions);

                int status = response != null ? response.status() : -1;

                if (status == 200) {
                    log.info("Request has accepted. ");
                    return;
                } else if (status == 403) {
                    throw new ForbiddenRequestException();
                }


            } catch (SefazAuthenticationException e) {
                throw e;
            } catch (Exception e) {
                boolean isNetworkError = isIsNetworkError(e);

                if (!isNetworkError) {
                    throw new SefazAuthenticationException("Authentication failed" + e.getMessage());
                }
            }

            if (i < maxRetries) {
                page.waitForTimeout(timeout);
            }

        }
        throw new SefazInvalidState("Maybe SEFAZ site is unreachable. Try again.  ");
    }

    private static boolean isIsNetworkError(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : "";
        boolean isNetworkError = message.contains("ERR_NETWORK_CHANGED")
                || message.contains("ERR_CONNECTION_RESET")
                || message.contains("ERR_CONNECTION_REFUSED")
                || message.contains("ERR_CONNECTION_TIMED_OUT")
                || message.contains("ERR_NAME_NOT_RESOLVED")
                || message.contains("ERR_SSL")
                || message.contains("ERR_ABORTED");
        return isNetworkError;
    }
}
