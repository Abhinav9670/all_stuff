package org.styli.services.customer.service.impl;

import java.security.NoSuchAlgorithmException;

import org.apache.commons.lang.StringUtils;
import org.springframework.dao.DataAccessException;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.pojo.registration.request.CustomerPasswordRequest;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerRestPassResponse;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.pojo.registration.response.PasswordResetResponse;
import org.styli.services.customer.service.Client;

public class ChangePassword {
    public CustomerRestPassResponse reset(CustomerPasswordRequest passwordReset, Client client,
            PasswordHelper passwordHelper) {

        CustomerRestPassResponse response = new CustomerRestPassResponse();

        if (!(StringUtils.isNotBlank(passwordReset.getCurrentPassword())
                && StringUtils.isNotBlank(passwordReset.getNewPassword()) && null != passwordReset.getCustomerId())) {

            response.setStatus(false);
            response.setStatusCode("204");
            response.setStatusMsg("Bad Request!!");

        } else {

            try {

                CustomerEntity customerEntity = client.findByEntityId(passwordReset.getCustomerId());

                String hash = customerEntity.getPasswordHash();

                final String DELIMITER = "\\:";
                String customerSalt = null;
                String hashVersion = null;

                if (null != hash) {

                    customerSalt = hash.split(DELIMITER)[1];
                    hashVersion = hash.split(DELIMITER)[2];
                }

                accordingToHashVersion(passwordReset, client, passwordHelper, response, customerEntity, hash,
						customerSalt, hashVersion);

            } catch (NoSuchAlgorithmException | DataAccessException exception) {

                ErrorType error = new ErrorType();
                error.setErrorCode("204");
                error.setErrorMessage(exception.getMessage());
                response.setError(error);
                response.setStatus(false);
                response.setStatusCode("500");
                response.setStatusMsg("ERROR !!");

            }

        }

        return response;

    }

	private void accordingToHashVersion(CustomerPasswordRequest passwordReset, Client client,
			PasswordHelper passwordHelper, CustomerRestPassResponse response, CustomerEntity customerEntity,
			String hash, String customerSalt, String hashVersion) throws NoSuchAlgorithmException {
		String createdHash;
		if (null != hashVersion && (hashVersion.equals("1") ||  hashVersion.equals("2"))) {

		    if (hashVersion.equals("1")) {

		        createdHash = passwordHelper.getSha256Hash(passwordReset.getCurrentPassword(), customerSalt);

		    } else {

		        createdHash = passwordHelper.getArgon2Id13Hash(passwordReset.getCurrentPassword(),
		                customerSalt);

		    }

		    processChangePassword(passwordReset, client, passwordHelper, response, customerEntity, hash,
					createdHash);

		} else {

		    response.setStatus(false);
		    response.setStatusCode("205");
		    response.setStatusMsg("Inavalid Hash Version!!");
		}
	}

	private void processChangePassword(CustomerPasswordRequest passwordReset, Client client,
			PasswordHelper passwordHelper, CustomerRestPassResponse response, CustomerEntity customerEntity,
			String hash, String createdHash) throws NoSuchAlgorithmException {
		if (null != createdHash && createdHash.equalsIgnoreCase(hash)) {

		    customerEntity
		            .setPasswordHash(passwordHelper.getSha256Hash(passwordReset.getNewPassword(), null));

		    client.saveAndFlushCustomerEntity(customerEntity);

		    PasswordResetResponse passwordRes = new PasswordResetResponse();
		    passwordRes.setValue(true);
		    response.setStatus(true);
		    response.setStatusCode("200");
		    response.setStatusMsg("Password Changhed Successfully!!");

		    response.setResponse(passwordRes);

		} else {
		    PasswordResetResponse passwordRes = new PasswordResetResponse();
		    passwordRes.setValue(false);

		    response.setResponse(passwordRes);
		    response.setStatus(true);
		    response.setStatusCode("201");
		    response.setStatusMsg("Inavalid Current Password!!");
		}
	}

}
