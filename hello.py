from flask import Flask
from flask import request
app = Flask(__name__)


@app.route('/', methods=['GET','POST'])
def hello():
    if request.method == 'POST':
        return add_dream()
    else:
        return print_dream()


def print_dream():
    html = '<html>'
    html += '<head><link rel="stylesheet" type="text/css" href="static/style.css"></head>'
    html += "<h3>The bucket list. Dream Big!</h3>"
    html +=  '''
            <form action="/" method="post">
            <label for="dream">Add item:</label>
            <input type="text" id="dream" name="dream"><br\ ><br\ >
            <input type="submit" value="Submit">
            </form> <br><br>
            ''' 
    html += "<h4>My bucket list:</h4>"
    try:
        with open('/app-storage/list.txt') as f:
            for line in f:
                html += f"{line} <br \>"
    except FileNotFoundError:
        html += "The list is empty. Go ahead - add the first item!"
    html += '</html>'
    return html


def add_dream():
    dream = request.form.get('dream')
    with open("/app-storage/list.txt", "a") as f:
        f.write(f"{dream}\n")
    html = print_dream()
    return html
