define(function () {
	var current = {

		configureSubscriptionParameters: function (configuration) {
			current.$super('registerXServiceSelect2')(configuration, 'service:qa:sonarqube:project', 'service/qa/sonarqube/');
		},

		/**
		 * Render SonarQube project identifier.
		 */
		renderKey: function (subscription) {
			return current.$super('renderKey')(subscription, 'service:qa:sonarqube:project');
		},

		/**
		 * Render SonarQube data.
		 */
		renderFeatures: function (subscription) {
			var result = current.$super('renderServiceLink')('home', subscription.parameters['service:qa:sonarqube:url'] + '/dashboard/index/' + encodeURIComponent(subscription.parameters['service:qa:sonarqube:project']), 'service:qa:sonarqube:project', undefined, ' target=\'_blank\'');
			// Help
			result += current.$super('renderServiceHelpLink')(subscription.parameters, 'service:qa:help');
			return result;
		},

		/**
		 * Render Sonar details : id, name and pkey.
		 */
		renderDetailsKey: function (subscription) {
			return current.$super('generateCarousel')(subscription, [
				['id', current.renderKey(subscription)],
				['service:qa:sonarqube:project', subscription.data.project.name],
				['service:qa:sonarqube:key', subscription.data.project.key]
			], 1);
		},

		/**
		 * Display the Sqale rating : A...E
		 */
		renderDetailsFeatures: function (subscription) {
		    return Object.keys(subscription.data.project?.measuresAsMap||{}).sort((m1, m2) => {
                if( m1.endsWith('_rating')) {
                    m1 = "_" + m1;
                }
                if( m2.endsWith('_rating')) {
                    m2 = "_" + m2;
                }
                if (m1 < m2) {
                    return -1;
                }
                if (m1 > m2) {
                    return 1;
                }
                return 0;
            }).map(m => {
			    let value = subscription.data.project.measuresAsMap[m];
			    let color = null;
			    let description = current.$messages[`service:qa:sonarqube:metric:${m}`];
			    let unit = current.$messages[`service:qa:sonarqube:metric:${m}:unit`];
			    let shortMetricName = m;
			    let displayValue = value;
			    if (m.endsWith('_rating')) {
			        const shortMetricName = m.substring(0, m.length-'_rating'.length);
			        description = description || current.$messages[`service:qa:sonarqube:metric:${shortMetricName}`] || shortMetricName;
    			    color = value && ['success', 'primary', 'warning', 'danger', 'danger', 'danger'][Math.floor(value) - 1];
    			    displayValue = String.fromCharCode(64 + value);
                }
                if (value > 1000) {
                    displayValue = Math.round(value/1000);
                    unit = 'K';
                }
                return `<span data-toggle="tooltip" title="${description||shortMetricName}" class="label label-${color||'default'}">${displayValue}${unit||''}</span>`;
            }).join(' ');
		}
	};
	return current;
});
