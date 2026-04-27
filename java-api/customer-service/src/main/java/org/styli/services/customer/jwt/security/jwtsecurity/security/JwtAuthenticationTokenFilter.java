package org.styli.services.customer.jwt.security.jwtsecurity.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.styli.services.customer.exception.BadRequestException;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtAuthenticationToken;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtUserDetails;
import org.styli.services.customer.pojo.LoginCredentials;
import org.styli.services.customer.utility.Constants;


public class JwtAuthenticationTokenFilter extends AbstractAuthenticationProcessingFilter {


	private static final Log LOGGER = LogFactory.getLog(JwtAuthenticationTokenFilter.class);

    final String jwtFlag;

	public JwtAuthenticationTokenFilter(String jwtFlag) {
        super("/rest/customer/auth/**");
        this.jwtFlag = jwtFlag;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) throws AuthenticationException, IOException, ServletException {
    	
    	//LoginCredentials   value = Constants.loginCredentials;
        String xClientVersion= httpServletRequest.getHeader(Constants.HEADER_X_CLIENT_VERSION);
        String xSource = httpServletRequest.getHeader(Constants.HEADER_X_SOURCE);
        boolean isJwtTokenEnable  = Constants.validateRefershTokenEnable(httpServletRequest, xClientVersion, xSource);
	    if("1".equals(jwtFlag) && isJwtTokenEnable && (StringUtils.isNotBlank(httpServletRequest.getHeader(Constants.deviceId))
	    		|| StringUtils.isNotBlank(httpServletRequest.getHeader(Constants.DeviceId)))) {
            try{
                String deviceId = null != httpServletRequest.getHeader(Constants.deviceId) ? httpServletRequest.getHeader(Constants.deviceId)
                        : httpServletRequest.getHeader(Constants.DeviceId);
                String headerToken = httpServletRequest.getHeader(Constants.Token);

                String uri = httpServletRequest.getRequestURI();

                LOGGER.info("uri:" + uri);

                if (deviceId == null || null != headerToken && headerToken.length() < 3 || null != headerToken
                        && !headerToken.startsWith(Constants.HEADDER_X_TOKEN_PREFIX)) {

                    throw new Exception(Constants.HEADDER_X_TOKEN_MISSING_MESSAGE);
                }

                String authenticationToken = null;

                if (null != headerToken && headerToken.length() > 3)

                    authenticationToken = headerToken.substring(4);

                JwtAuthenticationToken token = new JwtAuthenticationToken(authenticationToken);
                Authentication aunthetication = getAuthenticationManager().authenticate(token);

                authenticateUser(deviceId, headerToken, aunthetication);
                return aunthetication;
            }catch (AuthenticationException e){
                sendErrorResponse(httpServletResponse, e.getMessage(), 401);
                return null;
            } catch (Exception ex) {
                sendErrorResponse(httpServletResponse, ex.getMessage(), 401);
                return null;
            }
	    }else if("1".equals(jwtFlag)) {
            try{
                String headerEmail = httpServletRequest.getHeader(Constants.HEADDER_X_TOKEN);
                String headerToken = httpServletRequest.getHeader(Constants.HEADDER_X_HEADER_TOKEN);

                String uri = httpServletRequest.getRequestURI();

                LOGGER.info("uri:" + uri);

                if (headerEmail == null || null != headerToken && headerToken.length() < 3 || null != headerToken
                        && !headerToken.startsWith(Constants.HEADDER_X_TOKEN_PREFIX)) {

                    throw new Exception(Constants.HEADDER_X_TOKEN_MISSING_MESSAGE);
                }

                String authenticationToken = null;

                if (null != headerToken && headerToken.length() > 3)

                    authenticationToken = headerToken.substring(4);

                JwtAuthenticationToken token = new JwtAuthenticationToken(authenticationToken);
                Authentication aunthetication = getAuthenticationManager().authenticate(token);

                authenticateUser(headerEmail, headerToken, aunthetication);
                return aunthetication;
            }catch (AuthenticationException e){
                sendErrorResponse(httpServletResponse, e.getMessage(), 401);
                return null;
            } catch (Exception ex) {
                sendErrorResponse(httpServletResponse, ex.getMessage(), 401);
                return null;
            }
        } else {
            try{
    String header = httpServletRequest.getHeader("Token");

    if (header == null || !header.startsWith("KEY ")) {
        throw new Exception("JWT Token is missing");
    }
    String authenticationToken = header.substring(4);

    JwtAuthenticationToken token = new JwtAuthenticationToken(authenticationToken);
    return getAuthenticationManager().authenticate(token);
}catch (AuthenticationException e){
                sendErrorResponse(httpServletResponse, e.getMessage(), 401);
                return null;
            } catch (Exception ex) {
                sendErrorResponse(httpServletResponse, ex.getMessage(), 401);
                return null;
            }

        }
    }

	private void authenticateUser(String headerEmail, String headerToken, Authentication aunthetication) {

		if (StringUtils.isNotBlank(headerToken) && null != aunthetication && null != aunthetication.getPrincipal()) {

			JwtUserDetails jwtUser = (JwtUserDetails) aunthetication.getPrincipal();
			String trimEmail = getEmailFromHeader(headerEmail);

			if (!(null != jwtUser.getUsersName() && Constants.GUEST_EMAIL_IDS.contains(jwtUser.getUsersName()))) {

				if (StringUtils.isNotBlank(trimEmail) && !trimEmail.equalsIgnoreCase(jwtUser.getUsersName())
						&& !Constants.GUEST_EMAIL_IDS.contains(trimEmail) && !jwtUser.getUsersName().startsWith("+")) {
					LOGGER.info(" header token:" + trimEmail + " jwt user:" + jwtUser.getUsersName());
					throw new BadRequestException("403", Constants.EXCEPTION,
							Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);
				}
			}

		} else {

			LOGGER.info(" else header email :" + headerToken + " JWT token:" + headerToken);
			throw new BadRequestException("403", Constants.EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_MISSING_MESSAGE);

		}
	}

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            Authentication authResult) throws IOException, ServletException {
        super.successfulAuthentication(request, response, chain, authResult);
        chain.doFilter(request, response);
    }

    private  String getEmailFromHeader(String inputEmail) {
        String result = inputEmail;
        try {
            String[] chunks = inputEmail.split("_");
            if(chunks!=null && chunks.length>1) {
                ArrayList<String> chunksList = new ArrayList<>(Arrays.asList(chunks));
                for (int i = (chunksList.size()-1); i > (-1); i--) {
                    final String item = chunksList.get(i);
                    if(StringUtils.isNumericSpace(item)) {
                        chunksList.remove(i);
                    }else {
                        break;
                    }
                }
                String value = String.join("_", chunksList);
                if(value!=null) {
                    result = value.trim();
                } else {
                    result = "";
                }
            }
        } catch (Exception e)  {
            result = inputEmail;
        }
        return result;
    }
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode)throws IOException {

        response.setStatus(statusCode);

        response.setContentType("application/json");

        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(
                "{\"status\": false, \"statusCode\": " + statusCode + ", \"statusMsg\": \"" + message + "\"}"

        );

    }
}
