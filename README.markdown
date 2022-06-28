# Site Initializer Race Condition 7.4

This workspace has a site-initializer module and a site-creator module.

There is also a custom theme my-liferay-theme that only sets the background to blue.

The site-creator has a OSGi component SitesCreator, which calls the site initializer to create a site at the moment it activates.

### The Issue
It is often the case when the site is created before the theme becomes available, which causes the site to be created without the theme.

### How to test
- Run `gw initBundle`
- Run `gw deploy`
- Start the portal
- Open the Demo site

### Expected
The background for the Home page should be blue