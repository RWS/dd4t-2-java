/*
 * Copyright (c) 2015 SDL, Radagio & R. Oudshoorn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dd4t.mvc.controllers;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.dd4t.contentmodel.Page;
import org.dd4t.core.exceptions.FactoryException;
import org.dd4t.core.exceptions.ItemNotFoundException;
import org.dd4t.core.factories.impl.PageFactoryImpl;
import org.dd4t.core.util.Constants;
import org.dd4t.core.util.HttpUtils;
import org.dd4t.mvc.utils.RenderUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import jakarta.annotation.Resource;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * dd4t-2
 * 
 * Extend this class in your own web project for default functionality.
 * 
 * Do NOT add stuff here, as this will in the near future be loaded as library through maven only.
 *
 * @author R. Kempees
 */
@Controller
public abstract class AbstractPageController extends AbstractBaseController {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractPageController.class);

    @Resource
    protected PageFactoryImpl pageFactory;

    private String pageViewPath = "";

    /**
     * All page requests are handled by this method. The page meta XML is
     * queried based on the request URI, the page meta XML contains the actual
     * view name to be rendered.
     * 
     * Important Note: concrete implementing classes will need to add the
     * {@literal @RequestMapping} annotations!
     */

    public String showPage (Model model, HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String urlToFetch = HttpUtils.appendDefaultPageIfRequired(HttpUtils.getCurrentURL(request,this.removeContextPath));
        String url = adjustLocalErrorUrl(request, urlToFetch);
        url = HttpUtils.normalizeUrl(url);

        LOG.debug(">> {} page {} with dispatcher type {}", new Object[]{request.getMethod(), url, request.getDispatcherType().toString()});
        try {
            if (StringUtils.isEmpty(url)) {
                // url is not valid, throw an ItemNotFoundException
                throw new ItemNotFoundException("Page Url was empty or could not be resolved.");
            }

            Page pageModel = pageFactory.findPageByUrl(url, publicationResolver.getPublicationId());

            DateTime lastPublishDate = pageModel != null ? pageModel.getLastPublishedDate() : Constants.THE_YEAR_ZERO;

            response.setHeader(Constants.LAST_MODIFIED, createDateFormat().format(lastPublishDate.toDate()));

            model.addAttribute(Constants.REFERER, request.getHeader(HttpHeaders.REFERER));
            model.addAttribute(Constants.PAGE_MODEL_KEY, pageModel);
            model.addAttribute(Constants.PAGE_REQUEST_URI, HttpUtils.appendDefaultPageIfRequired(request.getRequestURI()));

            response.setContentType(HttpUtils.getContentTypeByExtension(url));
            return getPageViewName(pageModel);

        } catch (ItemNotFoundException e) {
            LOG.trace(e.getLocalizedMessage(), e);
            LOG.warn("Page with url '{}' could not be found.", url);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (FactoryException e) {
            if (e.getCause() instanceof ItemNotFoundException) {
                LOG.warn("Page with url '{}' could not be found.", url);
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } else {
                LOG.error("Factory Error.", e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

        return null;
    }

    private String adjustLocalErrorUrl (final HttpServletRequest request, final String url) {

        String adjustedUrl = url;
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            adjustedUrl = publicationResolver.getLocalPageUrl(url);
        }
        return adjustedUrl;
    }


    public String getPageViewName (final Page page) {
        String viewName;
        if (null != page.getPageTemplate().getMetadata() && page.getPageTemplate().getMetadata().containsKey("viewName")) {
            viewName = (String) page.getPageTemplate().getMetadata().get("viewName").getValues().get(0);
        } else {
            viewName = page.getPageTemplate().getTitle();
        }

        return RenderUtils.fixUrl(getPageViewPath() + viewName.trim());
    }

    /**
     * @return the pageViewPrefix
     */
    public String getPageViewPath () {
        return pageViewPath;
    }

    /**
     *
     */
    public void setPageViewPath (final String pageViewPath) {
        this.pageViewPath = pageViewPath;
    }


    public PageFactoryImpl getPageFactory () {
        return pageFactory;
    }

    public void setPageFactory (final PageFactoryImpl pageFactory) {
        this.pageFactory = pageFactory;
    }

}
