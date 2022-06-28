package com.liferay.site.creator;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.GroupConstants;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.module.framework.ModuleServiceLifecycle;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.site.exception.InitializationException;
import com.liferay.site.initializer.SiteInitializer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Leonardo Miyagi
 */
@Component(immediate = true, service = SitesCreator.class)
public class SitesCreator {

	@Activate
	public void activate() {
		if (_siteInitializer == null) {
			_log.info("Site Initializer not registered " + _INITIALIZER_KEY);

			return;
		}

		_createSite("/demo", "Demo", "Demo Site");
	}

	@Reference(
		target = "(site.initializer.key=" + _INITIALIZER_KEY + ")", unbind = "-"
	)
	public void setSiteInitializer(SiteInitializer siteInitializer) {
		_siteInitializer = siteInitializer;
	}

	private void _createSite(
		String friendlyUrl, String siteName, String siteDescription) {

		Group site = null;

		try {
			long companyId = _getDefaultCompanyId();

			site = _groupLocalService.fetchFriendlyURLGroup(
				companyId, friendlyUrl);

			if (site != null) {
				if (_log.isDebugEnabled()) {
					_log.debug("Site " + siteName + " already created");
				}

				return;
			}

			_log.info("Creating Site " + siteName);

			long userId = _getAdminUserId(companyId);

			User user = _userLocalService.getUser(userId);

			ServiceContext serviceContext = new ServiceContext();

			serviceContext.setCompanyId(companyId);
			serviceContext.setUserId(userId);

			site = _groupLocalService.addGroup(
				userId, GroupConstants.DEFAULT_PARENT_GROUP_ID,
				Group.class.getName(), userId,
				GroupConstants.DEFAULT_LIVE_GROUP_ID,
				_getLocalizedMap(LocaleUtil.getDefault(), siteName),
				_getLocalizedMap(LocaleUtil.getDefault(), siteDescription),
				GroupConstants.TYPE_SITE_OPEN, true,
				GroupConstants.DEFAULT_MEMBERSHIP_RESTRICTION, friendlyUrl,
				true, false, true, serviceContext);

			_initializeSite(site, userId, user, serviceContext);

			_log.info("Site " + siteName + " created");
		}
		catch (Exception exception) {
			_log.error("Error creating Site " + siteName, exception);

			try {
				_groupLocalService.deleteGroup(site);
			}
			catch (PortalException portalException) {
				_log.error("Error cleaning up Site", portalException);
			}
		}
	}

	private long _getAdminUserId(long companyId) throws PortalException {
		Company company = _companyLocalService.getCompanyById(companyId);

		User adminUser = company.getDefaultUser();

		return adminUser.getUserId();
	}

	private long _getDefaultCompanyId() throws PortalException {
		String webId = PropsUtil.get(PropsKeys.COMPANY_DEFAULT_WEB_ID);

		Company company = _companyLocalService.getCompanyByWebId(webId);

		return company.getCompanyId();
	}

	private Map<Locale, String> _getLocalizedMap(Locale locale, String name) {
		Map<Locale, String> map = new HashMap<>();

		map.put(locale, name);

		return map;
	}

	private void _initializeSite(
			Group myEGovSite, long userId, User user,
			ServiceContext serviceContext)
		throws InitializationException {

		String name = PrincipalThreadLocal.getName();

		PrincipalThreadLocal.setName(userId);

		ServiceContextThreadLocal.pushServiceContext(serviceContext);

		PermissionChecker permissionChecker =
			PermissionThreadLocal.getPermissionChecker();

		PermissionThreadLocal.setPermissionChecker(
			_liberalPermissionCheckerFactory.create(user));

		_siteInitializer.initialize(myEGovSite.getGroupId());

		PrincipalThreadLocal.setName(name);

		ServiceContextThreadLocal.popServiceContext();

		PermissionThreadLocal.setPermissionChecker(permissionChecker);
	}

	private static final String _INITIALIZER_KEY =
		"com.liferay.site.initializer.demo";

	private static final Log _log = LogFactoryUtil.getLog(SitesCreator.class);

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference(target = ModuleServiceLifecycle.PORTAL_INITIALIZED)
	private ModuleServiceLifecycle _moduleServiceLifecycle;

	@Reference(target = "(permission.checker.type=liberal)")
	private PermissionCheckerFactory _liberalPermissionCheckerFactory;

	private SiteInitializer _siteInitializer;

	@Reference
	private UserLocalService _userLocalService;

}