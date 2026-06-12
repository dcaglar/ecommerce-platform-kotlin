import yaml

def update_file(filename, is_base=False):
    with open(filename, 'r') as f:
        data = yaml.safe_load(f)
    
    if is_base:
        if 'mybatis' in data:
            del data['mybatis']
        if 'app' in data and 'datasource' in data['app']:
            del data['app']['datasource']
        # add base defaults
        if 'spring' not in data:
            data['spring'] = {}
        data['spring']['datasource'] = {
            'edge': {
                'driver-class-name': 'org.postgresql.Driver',
                'pool-name': 'edge-pool'
            },
            'yugabyte': {
                'driver-class-name': 'org.postgresql.Driver',
                'pool-name': 'yugabyte-pool'
            }
        }
    else:
        # copy app.datasource.web to spring.datasource.edge
        web_config = data.get('app', {}).get('datasource', {}).get('web', {})
        if web_config:
            if 'spring' not in data:
                data['spring'] = {}
            if 'datasource' not in data['spring']:
                data['spring']['datasource'] = {}
            
            data['spring']['datasource']['edge'] = web_config.copy()
            data['spring']['datasource']['yugabyte'] = web_config.copy()
            
            # Update specific properties for Yugabyte
            if 'local' in filename:
                data['spring']['datasource']['yugabyte']['jdbc-url'] = 'jdbc:postgresql://localhost:5433/yugabyte'
                data['spring']['datasource']['yugabyte']['username'] = 'yugabyte'
                data['spring']['datasource']['yugabyte']['password'] = 'yugabyte'
                data['spring']['datasource']['edge']['jdbc-url'] = '${EDGE_DB_URL:jdbc:postgresql://localhost:5432/edge-db}'
                data['spring']['datasource']['edge']['username'] = '${EDGE_DB_USERNAME:edge_db_user}'
                data['spring']['datasource']['edge']['password'] = '${EDGE_DB_PASSWORD:edge_db_password}'
            else:
                data['spring']['datasource']['yugabyte']['jdbc-url'] = 'jdbc:postgresql://yb-tserver.payment.svc.cluster.local:5433/yugabyte'
                data['spring']['datasource']['yugabyte']['username'] = '${YUGABYTE_DB_USERNAME:yugabyte}'
                data['spring']['datasource']['yugabyte']['password'] = '${YUGABYTE_DB_PASSWORD:yugabyte}'
                data['spring']['datasource']['edge']['jdbc-url'] = '${EDGE_DB_URL}'
                data['spring']['datasource']['edge']['username'] = '${EDGE_DB_USERNAME}'
                data['spring']['datasource']['edge']['password'] = '${EDGE_DB_PASSWORD}'
            
            del data['app']['datasource']

    with open(filename, 'w') as f:
        yaml.dump(data, f, default_flow_style=False, sort_keys=False)

update_file('payment-service/src/main/resources/application.yml', True)
update_file('payment-service/src/main/resources/application-local.yml', False)
update_file('payment-service/src/main/resources/application-azure.yml', False)
