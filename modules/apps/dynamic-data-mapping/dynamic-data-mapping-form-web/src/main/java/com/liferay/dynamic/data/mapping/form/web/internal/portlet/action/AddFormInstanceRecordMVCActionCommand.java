/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.dynamic.data.mapping.form.web.internal.portlet.action;

import com.liferay.captcha.util.CaptchaUtil;
import com.liferay.dynamic.data.mapping.constants.DDMPortletKeys;
import com.liferay.dynamic.data.mapping.exception.FormInstanceNotPublishedException;
import com.liferay.dynamic.data.mapping.form.evaluator.DDMFormEvaluator;
import com.liferay.dynamic.data.mapping.form.evaluator.DDMFormEvaluatorEvaluateRequest;
import com.liferay.dynamic.data.mapping.form.evaluator.DDMFormEvaluatorEvaluateResponse;
import com.liferay.dynamic.data.mapping.form.field.type.DDMFormFieldOptionsFactory;
import com.liferay.dynamic.data.mapping.form.values.factory.DDMFormValuesFactory;
import com.liferay.dynamic.data.mapping.form.web.internal.constants.DDMFormWebKeys;
import com.liferay.dynamic.data.mapping.form.web.internal.portlet.action.helper.AddFormInstanceRecordMVCCommandHelper;
import com.liferay.dynamic.data.mapping.form.web.internal.util.DDMLayoutUtil;
import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.dynamic.data.mapping.model.DDMFormInstance;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecord;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecordVersion;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceSettings;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.model.Value;
import com.liferay.dynamic.data.mapping.render.DDMFormFieldRenderingContext;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceRecordService;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceRecordVersionLocalService;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceService;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceVersionLocalService;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.mail.kernel.model.MailMessage;
import com.liferay.mail.kernel.service.MailService;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCActionCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextFactory;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.PrefsProps;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.ResourceBundleUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

import javax.mail.internet.InternetAddress;
import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletSession;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Marcellus Tavares
 */
@Component(
	property = {
		"javax.portlet.name=" + DDMPortletKeys.DYNAMIC_DATA_MAPPING_FORM,
		"javax.portlet.name=" + DDMPortletKeys.DYNAMIC_DATA_MAPPING_FORM_ADMIN,
		"mvc.command.name=/dynamic_data_mapping_form/add_form_instance_record"
	},
	service = MVCActionCommand.class
)
public class AddFormInstanceRecordMVCActionCommand
	extends BaseMVCActionCommand {

	private static final Map<String, Map<String, String>> messages =
		HashMapBuilder.<String, Map<String, String>>create(2)
			.put(
				"hr",
				HashMapBuilder.<String, String>create(2)
					.put(
						"anonymous-user-email-confirmation-subject",
						"{0} potvrda")
					.put(
						"anonymous-user-email-confirmation-body",
						"Poštovani,<br/> Vaši podaci su uspješno primljeni. Hvala što ste ispunili obrazac. Možete uređivati svoje podatke <a href='{0}'>ovdje</a>.")
					.build())
			.put(
				"en",
				HashMapBuilder.<String, String>create(2)
					.put(
						"anonymous-user-email-confirmation-subject",
						"{0} Confirmation")
					.put(
						"anonymous-user-email-confirmation-body",
						"Dear,<br/> your information was successfully received. Thank you for filling out the form. You can edit your data <a href='{0}'>here</a>.")
					.build())
			.build();

	@Override
	protected void doProcessAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		PortletSession portletSession = actionRequest.getPortletSession();

		long groupId = ParamUtil.getLong(actionRequest, "groupId");

		if (groupId == 0) {
			groupId = GetterUtil.getLong(
				portletSession.getAttribute(DDMFormWebKeys.GROUP_ID));
		}

		long formInstanceId = ParamUtil.getLong(
			actionRequest, "formInstanceId");

		if (formInstanceId == 0) {
			formInstanceId = GetterUtil.getLong(
				portletSession.getAttribute(
					DDMFormWebKeys.DYNAMIC_DATA_MAPPING_FORM_INSTANCE_ID));
		}

		DDMFormInstance ddmFormInstance =
			_ddmFormInstanceService.getFormInstance(formInstanceId);

		_addFormInstanceMVCCommandHelper.validateExpirationStatus(
			ddmFormInstance, actionRequest);
		_addFormInstanceMVCCommandHelper.validateSubmissionLimitStatus(
			ddmFormInstance, _ddmFormInstanceRecordVersionLocalService,
			actionRequest);

		_validatePublishStatus(actionRequest, ddmFormInstance);

		_validateCaptcha(actionRequest, ddmFormInstance);

		DDMForm ddmForm = getDDMForm(ddmFormInstance);

		DDMFormValues ddmFormValues = _ddmFormValuesFactory.create(
			actionRequest, ddmForm);

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		_createDDMFormFieldOptionsFromDataProvider(
			actionRequest, ddmForm, themeDisplay.getLocale());

		DDMFormEvaluatorEvaluateResponse ddmFormEvaluatorEvaluateResponse =
			_ddmFormEvaluator.evaluate(
				DDMFormEvaluatorEvaluateRequest.Builder.newBuilder(
					ddmForm, ddmFormValues,
					LocaleUtil.fromLanguageId(
						_language.getLanguageId(actionRequest))
				).withCompanyId(
					_portal.getCompanyId(actionRequest)
				).withDDMFormInstanceId(
					ParamUtil.getLong(actionRequest, "formInstanceId")
				).withGroupId(
					ParamUtil.getLong(actionRequest, "groupId")
				).withTimeZoneId(
					_getTimeZoneId(themeDisplay)
				).withUserId(
					_portal.getUserId(actionRequest)
				).build());

		DDMStructure ddmStructure = ddmFormInstance.getStructure();

		_addFormInstanceMVCCommandHelper.updateNonevaluableDDMFormFields(
			ddmForm.getDDMFormFieldsMap(true),
			ddmFormEvaluatorEvaluateResponse.getDDMFormFieldsPropertyChanges(),
			ddmFormValues.getDDMFormFieldValuesMap(true),
			ddmStructure.getDDMFormLayout(),
			ddmFormEvaluatorEvaluateResponse.getDisabledPagesIndexes());

		_addFormInstanceMVCCommandHelper.updateReadOnlyDDMFormFields(
			ddmForm.getDDMFormFieldsMap(true),
			ddmFormEvaluatorEvaluateResponse.getDDMFormFieldsPropertyChanges());

		ServiceContext serviceContext = ServiceContextFactory.getInstance(
			DDMFormInstanceRecord.class.getName(), actionRequest);

		serviceContext.setRequest(_portal.getHttpServletRequest(actionRequest));

		_updateFormInstanceRecord(
			actionRequest, ddmFormInstance, ddmFormValues, groupId,
			serviceContext, themeDisplay.getUserId());

		if (!SessionErrors.isEmpty(actionRequest)) {
			return;
		}

		if (SessionMessages.contains(
				actionRequest,
				_portal.getPortletId(actionRequest) +
					SessionMessages.KEY_SUFFIX_HIDE_DEFAULT_SUCCESS_MESSAGE)) {

			SessionMessages.clear(actionRequest);
		}

		SessionMessages.add(actionRequest, "formInstanceRecordAdded");

		DDMFormInstanceSettings ddmFormInstanceSettings =
			ddmFormInstance.getSettingsModel();

		String ddmFormInstanceSettingsRedirectURL =
			ddmFormInstanceSettings.redirectURL();

		if (Validator.isNotNull(ddmFormInstanceSettingsRedirectURL)) {
			hideDefaultSuccessMessage(actionRequest);
		}

		portletSession.setAttribute(
			DDMFormWebKeys.DYNAMIC_DATA_MAPPING_FORM_INSTANCE_ID,
			formInstanceId);
		portletSession.setAttribute(DDMFormWebKeys.GROUP_ID, groupId);

		sendRedirect(
			actionRequest, actionResponse,
			ParamUtil.getString(
				actionRequest, "redirect", ddmFormInstanceSettingsRedirectURL));
	}

	protected DDMForm getDDMForm(DDMFormInstance ddmFormInstance)
		throws PortalException {

		DDMStructure ddmStructure = ddmFormInstance.getStructure();

		return ddmStructure.getDDMForm();
	}

	private void _createDDMFormFieldOptionsFromDataProvider(
		ActionRequest actionRequest, DDMForm ddmForm, Locale locale) {

		DDMFormFieldRenderingContext ddmFormFieldRenderingContext =
			new DDMFormFieldRenderingContext();

		ddmFormFieldRenderingContext.setHttpServletRequest(
			_portal.getHttpServletRequest(actionRequest));
		ddmFormFieldRenderingContext.setLocale(locale);

		Map<String, DDMFormField> ddmFormFieldsMap =
			ddmForm.getDDMFormFieldsMap(true);

		for (DDMFormField ddmFormField : ddmFormFieldsMap.values()) {
			if (Objects.equals(ddmFormField.getType(), "select") &&
				Objects.equals(
					ddmFormField.getDataSourceType(), "data-provider")) {

				ddmFormField.setProperty(
					"options",
					_ddmFormFieldOptionsFactory.create(
						ddmFormField, ddmFormFieldRenderingContext));
			}
		}
	}

	private String _getEmailFromAddress(DDMFormInstance ddmFormInstance)
		throws Exception {

		DDMFormInstanceSettings formInstancetings =
			ddmFormInstance.getSettingsModel();

		String defaultEmailFromAddress = _prefsProps.getString(
			ddmFormInstance.getCompanyId(), PropsKeys.ADMIN_EMAIL_FROM_ADDRESS);

		return GetterUtil.getString(
			Objects.equals(formInstancetings.emailFromAddress(), "")
				? null
				: formInstancetings.emailFromAddress(),
			defaultEmailFromAddress);
	}

	private String _getEmailFromName(DDMFormInstance ddmFormInstance)
		throws Exception {

		DDMFormInstanceSettings formInstancetings =
			ddmFormInstance.getSettingsModel();

		String defaultEmailFromName = _prefsProps.getString(
			ddmFormInstance.getCompanyId(), PropsKeys.ADMIN_EMAIL_FROM_NAME);

		return GetterUtil.getString(
			Objects.equals(formInstancetings.emailFromName(), "")
				? null
				: formInstancetings.emailFromName(),
			defaultEmailFromName);
	}

	private String _getTimeZoneId(ThemeDisplay themeDisplay) {
		if (themeDisplay == null) {
			return StringPool.BLANK;
		}

		User user = themeDisplay.getUser();

		return user.getTimeZoneId();
	}

	private void _sendConfirmationMail(
			DDMFormInstance ddmFormInstance,
			DDMFormInstanceRecord ddmFormInstanceRecord, String emailToAddress,
			ThemeDisplay themeDisplay)
		throws Exception {

		MailMessage mailMessage = new MailMessage();

		mailMessage.setFrom(
			new InternetAddress(
				_getEmailFromAddress(ddmFormInstance),
				_getEmailFromName(ddmFormInstance)));
		mailMessage.setTo(new InternetAddress(emailToAddress));

//		ResourceBundle resourceBundle = ResourceBundleUtil.getBundle(
//			"content.Language", themeDisplay.getLocale(), getClass());
//
//		mailMessage.setSubject(
//			_language.format(
//				resourceBundle, "anonymous-user-email-confirmation-subject",
//				ddmFormInstance.getName(themeDisplay.getLocale()), false));
//		mailMessage.setBody(
//			_language.format(
//				resourceBundle, "anonymous-user-email-confirmation-body",
//				PortalUtil.getLayoutURL(themeDisplay) + "?uuid=" +
//				ddmFormInstanceRecord.getUuid(), false));

		Locale locale = themeDisplay.getLocale();

		mailMessage.setSubject(
				messages
					.get(locale.getLanguage())
					.get("anonymous-user-email-confirmation-subject")
					.replace(
						"{0}",
						ddmFormInstance.getName(themeDisplay.getLocale())));
		mailMessage.setBody(
			messages
				.get(locale.getLanguage())
				.get("anonymous-user-email-confirmation-body")
				.replace(
					"{0}",
					PortalUtil.getLayoutURL(themeDisplay) + "?uuid=" +
					ddmFormInstanceRecord.getUuid()));
		mailMessage.setHTMLFormat(true);

		_mailService.sendEmail(mailMessage);
	}

	private void _updateFormInstanceRecord(
			ActionRequest actionRequest, DDMFormInstance ddmFormInstance,
			DDMFormValues ddmFormValues, long groupId,
			ServiceContext serviceContext, long userId)
		throws Exception {

		long ddmFormInstanceRecordId = ParamUtil.getLong(
			actionRequest, "formInstanceRecordId");

		if (ddmFormInstanceRecordId != 0) {
			_ddmFormInstanceRecordService.updateFormInstanceRecord(
				ddmFormInstanceRecordId, false, ddmFormValues, serviceContext);
		}
		else {
			DDMFormInstanceRecordVersion ddmFormInstanceRecordVersion =
				_ddmFormInstanceRecordVersionLocalService.
					fetchLatestFormInstanceRecordVersion(
						userId, ddmFormInstance.getFormInstanceId(),
						ddmFormInstance.getVersion(),
						WorkflowConstants.STATUS_DRAFT);

			DDMFormInstanceRecord ddmFormInstanceRecord;

			if (ddmFormInstanceRecordVersion == null) {
				ddmFormInstanceRecord =
					_ddmFormInstanceRecordService.addFormInstanceRecord(
						groupId, ddmFormInstance.getFormInstanceId(),
						ddmFormValues, serviceContext);
			}
			else {
				ddmFormInstanceRecord =
					_ddmFormInstanceRecordService.updateFormInstanceRecord(
						ddmFormInstanceRecordVersion.getFormInstanceRecordId(),
						false, ddmFormValues, serviceContext);
			}

			ThemeDisplay themeDisplay =
				(ThemeDisplay) actionRequest.getAttribute(
					WebKeys.THEME_DISPLAY);

			String emailToAddress = _getEmailToAddress(
				ddmFormValues, themeDisplay);

			if (emailToAddress != null) {
				_sendConfirmationMail(
					ddmFormInstance, ddmFormInstanceRecord, emailToAddress,
					themeDisplay);
			}
		}
	}

	private static String _getEmailToAddress(
		DDMFormValues ddmFormValues, ThemeDisplay themeDisplay) {
		DDMFormFieldValue emailDDMFormFieldValue = null;

		for (DDMFormFieldValue ddmFormFieldValue :
			ddmFormValues.getDDMFormFieldValues()) {

			if (Objects.equals(
				ddmFormFieldValue.getFieldReference(), "emailAddress")) {

				emailDDMFormFieldValue = ddmFormFieldValue;

				break;
			}
		}

		String emailToAddress = null;

		if (emailDDMFormFieldValue != null) {
			Value value = emailDDMFormFieldValue.getValue();

			Map<Locale, String> localizedValueMap = value.getValues();

			emailToAddress = localizedValueMap.get(themeDisplay.getLocale());
		}

		return emailToAddress;
	}

	private void _validateCaptcha(
			ActionRequest actionRequest, DDMFormInstance ddmFormInstance)
		throws Exception {

		DDMFormInstanceSettings formInstanceSettings =
			ddmFormInstance.getSettingsModel();

		if (formInstanceSettings.requireCaptcha()) {
			CaptchaUtil.check(actionRequest);
		}
	}

	private void _validatePublishStatus(
			ActionRequest actionRequest, DDMFormInstance ddmFormInstance)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		String currentURL = ParamUtil.getString(actionRequest, "currentURL");

		DDMFormInstanceSettings ddmFormInstanceSettings =
			ddmFormInstance.getSettingsModel();

		if (StringUtil.startsWith(
				currentURL, DDMLayoutUtil.getFormLayoutURL(themeDisplay)) &&
			!ddmFormInstanceSettings.published()) {

			throw new FormInstanceNotPublishedException(
				"Form instance " + ddmFormInstance.getFormInstanceId() +
					" is not published");
		}
	}

	@Reference
	private AddFormInstanceRecordMVCCommandHelper
		_addFormInstanceMVCCommandHelper;

	@Reference
	private DDMFormEvaluator _ddmFormEvaluator;

	@Reference
	private DDMFormFieldOptionsFactory _ddmFormFieldOptionsFactory;

	@Reference
	private DDMFormInstanceRecordService _ddmFormInstanceRecordService;

	@Reference
	private DDMFormInstanceRecordVersionLocalService
		_ddmFormInstanceRecordVersionLocalService;

	@Reference
	private DDMFormInstanceService _ddmFormInstanceService;

	@Reference
	private DDMFormInstanceVersionLocalService
		_ddmFormInstanceVersionLocalService;

	@Reference
	private DDMFormValuesFactory _ddmFormValuesFactory;

	@Reference
	private Language _language;

	@Reference
	private MailService _mailService;

	@Reference
	private PrefsProps _prefsProps;

	@Reference
	private Portal _portal;

}