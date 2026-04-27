
#Preparing env for scripts 
 
mkdir /tmp/git
JIRA=$(echo -n $JIRA | base64 -d)
JIRA=\"${JIRA}\"
echo $JIRA
 echo ${LAST_TAG}..${tag}
#gitloggeneration
echo "$SSH_KEY" > gitrsa
chmod 400 gitrsa
eval ssh-agent -s
ssh-agent sh -c 'ssh-add -k gitrsa; ssh -T git@github.com; git clone git@github.com:Styli-dev/${reponame}.git /tmp/git && cd /tmp/git;LAST_TAG=$(git tag | sort -r | sed -n '2p'); git log ${LAST_TAG}..${tag} | sort | uniq  > changelogtmp.txt'
cd /tmp/git
echo "changelog to trim"
cat changelogtmp.txt
printf "grep -E $JIRA < changelogtmp.txt > changelog.txt" | sed "s/[']//g" > change.bash
chmod u+x change.bash
./change.bash
echo "changelog after trim"
cat changelog.txt
echo "printing tag name"
echo $tag


changes=$(cat changelog.txt)
echo "changes are $changes"
CHANGE=$(printf "%s" "$changes" | base64 -w 0)
cat changelog.txt | awk '{print $1}' |sed 's/["\]/\\&/g;s/.*/"&"/;1s/^/[/;$s/$/]/;$!s/$/,/' | tr '\n' ' ' > jiras
jiras=$(cat jiras)
echo $jiras

curl -X POST -H 'content-type: application/json' \
    --data '{"issues":'"${jiras}"', "data": {"releaseVersion":"'"${tag}"'"}}' \
    https://automation.atlassian.com/pro/hooks/30c77be7b4c0bca321758c7521863419967395aa

#while read -r line; do
 #     issue='{"issues":["'"${line}"'"]}'
  #  curl -X POST -H 'content-type: application/json' \
   # --data $issue \
    #https://automation.atlassian.com/pro/hooks/30c77be7b4c0bca321758c7521863419967395aa
   # done < "changelog.txt"
